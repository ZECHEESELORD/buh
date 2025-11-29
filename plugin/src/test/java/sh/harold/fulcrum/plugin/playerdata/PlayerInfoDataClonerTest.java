package sh.harold.fulcrum.plugin.playerdata;

import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedProfilePublicKey;
import com.comphenix.protocol.wrappers.WrappedRemoteChatSessionData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerInfoDataClonerTest {

    @Test
    void clonePreservesNonNameFields() throws Exception {
        UUID profileId = UUID.randomUUID();
        WrappedGameProfile profile = mock(WrappedGameProfile.class);
        when(profile.getUUID()).thenReturn(profileId);
        when(profile.getName()).thenReturn("Vanilla");
        WrappedChatComponent originalDisplay = null;
        WrappedProfilePublicKey.WrappedProfileKeyData keyData = mock(WrappedProfilePublicKey.WrappedProfileKeyData.class);
        WrappedRemoteChatSessionData remote = null;

        PlayerInfoData source = new PlayerInfoData(
            profileId,
            42,
            true,
            EnumWrappers.NativeGameMode.CREATIVE,
            profile,
            originalDisplay,
            true,
            5,
            remote
        );

        Field keyField = PlayerInfoData.class.getDeclaredField("profileKeyData");
        keyField.setAccessible(true);
        keyField.set(source, keyData);

        Component updatedDisplay = null;
        PlayerInfoData cloned = PlayerInfoDataCloner.cloneWithDisplayName(
            source,
            updatedDisplay,
            GsonComponentSerializer.gson(),
            Logger.getLogger("test")
        );

        assertThat(cloned.getProfileId()).isEqualTo(profileId);
        assertThat(cloned.getLatency()).isEqualTo(source.getLatency());
        assertThat(cloned.isListed()).isEqualTo(source.isListed());
        assertThat(cloned.getGameMode()).isEqualTo(source.getGameMode());
        assertThat(cloned.getProfile()).isEqualTo(profile);
        assertThat(cloned.isShowHat()).isEqualTo(source.isShowHat());
        assertThat(cloned.getListOrder()).isEqualTo(source.getListOrder());
        assertThat(cloned.getRemoteChatSessionData()).isEqualTo(remote);
        assertThat(cloned.getProfileKeyData()).isEqualTo(keyData);
        assertThat(cloned.getDisplayName()).isNull();
    }
}
