package sh.harold.fulcrum.plugin.playerdata;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsernameBaseNameResolverTest {

    @Mock
    private PlayerSettingsService settingsService;

    @Mock
    private LinkedAccountService linkedAccountService;

    @Test
    void minecraftViewUsesVanillaName() {
        UsernameBaseNameResolver resolver = new UsernameBaseNameResolver(settingsService, linkedAccountService);
        UUID viewer = UUID.randomUUID();
        when(settingsService.cachedUsernameView(viewer)).thenReturn(UsernameView.MINECRAFT);

        UsernameBaseNameResolver.BaseName resolved = resolver.resolve(viewer, viewer, "Steve");

        assertThat(resolved.value()).isEqualTo("Steve");
        assertThat(resolved.component()).isEqualTo(Component.text("Steve").decoration(TextDecoration.ITALIC, false));
    }

    @Test
    void osuViewPicksAlias() {
        UUID target = UUID.randomUUID();
        UsernameBaseNameResolver resolver = new UsernameBaseNameResolver(settingsService, linkedAccountService);
        when(linkedAccountService.osuUsername(target)).thenReturn(Optional.of("osu-alias"));

        UsernameBaseNameResolver.BaseName resolved = resolver.resolve(UsernameView.OSU, target, "Steve");

        assertThat(resolved.value()).isEqualTo("osu-alias");
    }

    @Test
    void discordViewFallsBackToVanillaWhenMissing() {
        UUID target = UUID.randomUUID();
        UsernameBaseNameResolver resolver = new UsernameBaseNameResolver(settingsService, linkedAccountService);
        when(linkedAccountService.discordDisplayName(target)).thenReturn(Optional.empty());

        UsernameBaseNameResolver.BaseName resolved = resolver.resolve(UsernameView.DISCORD, target, "Alex");

        assertThat(resolved.value()).isEqualTo("Alex");
    }

    @Test
    void bestAliasFallbackIsUsedWhenRequested() {
        UUID target = UUID.randomUUID();
        UsernameBaseNameResolver resolver = new UsernameBaseNameResolver(settingsService, linkedAccountService);
        when(linkedAccountService.osuUsername(target)).thenReturn(Optional.empty());
        when(linkedAccountService.bestAlias(target)).thenReturn(Optional.of("backup"));

        UsernameBaseNameResolver.BaseName resolved = resolver.resolveWithBestAliasFallback(UsernameView.OSU, target, "Vanilla");

        assertThat(resolved.value()).isEqualTo("backup");
    }
}
