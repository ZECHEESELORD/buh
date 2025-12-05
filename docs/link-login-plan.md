Discord to osu Link Plan:
We want Discord users to land in osu, return with a code, and leave with a verified Minecraft account plus LuckPerms role. The path is straightforward; the scenery deserves a map.

Module boundary:
This whole feature lives in a dedicated account-link module (Discord/osu ticketing, state signing, pre-login gate, and ticket consumption). Core player data and other modules remain untouched.

Flow sketch:
1) Trigger on Discord: modal collects Minecraft username, Mojang lookup gates the path, payload {discordId, uuid} gets HMAC SHA256, Base64 URL encoded, sent as an ephemeral osu OAuth link.
2) Callback on web: decode state, verify HMAC with current secret, exchange code for osu profile (id, username, rank, country), reject on verification failure.
3) Server logic: check if uuid already belongs to another Discord id; if yes, reject. If this Discord id owns a different uuid, delete that link and remove the old name from whitelist. Write a link request (keyed by uuid) with discordId, osu payload, Mojang username snapshot, and moderation state. Use the Mojang username to whitelist add and set LuckPerms parent verified on the main thread so the player can join, but leave the player document untouched until first login.

Data shape in link_requests collection (one per uuid, latest wins):
```json
{
  "userId": 123456789012345678,
  "username": "DiscordUsername",
  "source": "Invited By Friend",
  "invitedBy": "SponsorName",
  "minecraft": {
    "uuid": "069a...",
    "username": "CurrentMojangName"
  },
  "osu": {
    "userId": 987654,
    "username": "osuName",
    "rank": 1234,
    "country": "CA"
  },
  "createdAt": "2024-01-02T03:04:05Z",
  "status": "PENDING",
  "requestMessageId": 123456789012345678,
  "consumed": false
}
```
Player document stays lean: when the player first joins, the join pipeline creates the doc with meta and defaults, then consumes any request entry for that uuid and writes `discordId` plus `osu` into the player doc root.

Security and resilience:
* State includes nonce and issuedAt; short TTL (target fifteen minutes) and secret versioning to kill replays.
* Cache Mojang lookups briefly to dodge rate limits and retry 5xx before failing.
* Keep secrets in env or config, not in code. Rotate by checking the latest secret version when verifying state.
* Fail fast on anti theft; do not enqueue whitelist or LP if the check fails.

HTTP defaults:
* Bind: 0.0.0.0:8151
* Callback: http://localhost:8151/callback

Concurrency and consistency:
* Add a tiny secondary collection (discord_links) keyed by discordId -> uuid to avoid scanning players. Use it to enforce one to one mapping and to resolve overwrite cases quickly.
* Keep player document as the source of truth; the secondary index mirrors it for lookup speed.
* Main thread only for whitelist and LuckPerms calls; record failures for retry so the link is not lost.

Persistence and fields to refresh:
* Always refresh meta.username from Mojang when linking so whitelist and LP use the latest name.
* Store osu userId, username, rank, country in osu for future display or refresh. Keep rank as integer, country as ISO alpha 2 string.

Observability:
* Log attempts with discordId, uuid, outcome, reason on failure.
* Consider a short history array if we need to audit relinks; otherwise omit for now.

Implementation outline (future work, not started):
1) Add storage helpers and a tiny link_requests collection plus the discord_links secondary collection for lookups.
2) Implement state signer and validator with nonce, timestamp, secret version, Base64 URL encoding.
3) Bot: Discord modal flow, Mojang gate, state creation, osu link response.
4) Web callback handler: verify state, exchange code, fetch osu data, call plugin endpoint.
5) Plugin endpoint: anti theft check, overwrite handling, write link ticket, schedule whitelist add and LP role set using Mojang username. Optionally still call vanilla `whitelist add` for compatibility.
6) Join path: on first login, create player document via existing pipeline, then consume ticket to write discordId and osu into the player doc, delete the ticket, and allow join. If no matching ticket exists, block the join with a clear message (UUID based gate, independent of vanilla whitelist).
7) Error paths: clear any temporary state, surface clear user facing messages, leave data consistent.

Open decisions to lock in:
1) Exact TTL for signed state.
2) Confirm using discord_links collection for uniqueness instead of scanning players, and whether it mirrors tickets or final links.
3) osu scopes to request; we only need user profile basics for username, rank, country.
4) Behaviour when whitelist or LP command fails after persistence: retry queue or manual reconciliation notice.

Migration plan:
1) Enforce immediately: pre login gate requires a link ticket or existing player doc with discordId and osu; otherwise block join with a clear message that points to the Discord flow.
2) In game prompt: when blocked, surface concise instructions (e.g., “Link your account via /link in Discord; click the osu button after entering your Minecraft name.”).
3) Comms: announce in Discord that linking with osu is required and provide the command or button path.
4) As players link, tickets are created and consumed on next join, populating discordId and osu in their player doc.
5) Optional cleanup: once the population migrates, scrub any old vanilla whitelist entries that no longer correspond to linked players if we decide to keep whitelist in sync.
