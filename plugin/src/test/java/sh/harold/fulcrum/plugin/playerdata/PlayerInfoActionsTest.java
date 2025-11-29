package sh.harold.fulcrum.plugin.playerdata;

import com.comphenix.protocol.wrappers.EnumWrappers;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerInfoActionsTest {

    @Test
    void updateDisplayNameUsesEnumSet() {
        EnumSet<EnumWrappers.PlayerInfoAction> actions = PlayerInfoActions.updateDisplayName();
        assertThat(actions).isInstanceOf(EnumSet.class);
        assertThat(actions).containsExactly(EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME);
    }
}
