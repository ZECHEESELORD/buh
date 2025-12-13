package sh.harold.fulcrum.plugin.unlockable;

public sealed interface Cosmetic permits ActionCosmetic, ChatPrefixCosmetic, MenuSkinCosmetic, OsuRankChatPrefixCosmetic, ParticleTrailCosmetic, StatusCosmetic {

    UnlockableDefinition definition();

    CosmeticSection section();

    default UnlockableId id() {
        return definition().id();
    }
}
