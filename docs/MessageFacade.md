Message Facade:
An intentionally tiny facade for colored server messages; it keeps Quick Maths parity without loading any translation bundles or external config.

Pieces:
1. Message: static success, info, debug, error helpers that use MessageFormat when args are present, then hand off to a builder.
2. MessageBuilder: accepts optional MessageTag prefixes, returns an Adventure Component that stitches tags before the colored body, and can send to Audience or CommandSender; staff() is a convenience for the staff tag.
3. MessageStyle: hardcoded colors only, one per style.
4. MessageTag: STAFF prefix in aqua, DEBUG prefix in dark gray.

Usage:
1. Message.success("Started Quick Maths for {0}", scope).tag(MessageTag.STAFF).send(sender);
2. Message.error("Number of winners must be between 1 and {0}.", maxWinners).send(sender);
