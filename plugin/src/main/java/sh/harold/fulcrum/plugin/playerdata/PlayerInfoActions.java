package sh.harold.fulcrum.plugin.playerdata;

import com.comphenix.protocol.wrappers.EnumWrappers;

import java.util.EnumSet;

final class PlayerInfoActions {

    private PlayerInfoActions() {
    }

    static EnumSet<EnumWrappers.PlayerInfoAction> updateDisplayName() {
        return EnumSet.of(EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME);
    }
}
