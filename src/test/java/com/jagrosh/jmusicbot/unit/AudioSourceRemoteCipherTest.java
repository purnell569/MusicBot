/*
 * Copyright 2026 Arif Banai (arif-banai)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.unit;

import com.jagrosh.jmusicbot.BotConfig;
import dev.lavalink.youtube.YoutubeSourceOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression tests for remote cipher configuration in AudioSource.
 *
 * <p>The bug: {@code buildYoutubeOptions} only applied the remote cipher when
 * YouTube OAuth was enabled. Since OAuth defaults to {@code false}, virtually every
 * install fell back to youtube-source's bundled {@code LocalSignatureCipherManager},
 * whose extraction patterns stop matching whenever YouTube rotates its player script.
 * Playback then died with:
 *
 * <pre>
 *   Client [MWEB] failed: Must find sig function from script:
 *     /s/player/8c3fda2d/player_embed.vflset/nl_NL/base.js
 * </pre>
 *
 * <p>Deciphering and authentication are unrelated concerns, so the cipher must be
 * applied regardless of the OAuth setting.
 *
 * @see com.jagrosh.jmusicbot.audio.AudioSource
 */
@DisplayName("AudioSource Remote Cipher Tests")
class AudioSourceRemoteCipherTest {

    private static final String CIPHER_URL = "https://cipher.kikkia.dev/";

    @Test
    @DisplayName("Remote cipher is configured when OAuth is disabled - regression test")
    void remoteCipherConfiguredWhenOAuthDisabled() throws Exception {
        // The key regression. Before the fix this returned null, leaving the bot on
        // the local extractor and breaking playback on every player script rotation.
        YoutubeSourceOptions options = buildOptions(mockConfig(false, CIPHER_URL, ""));

        assertEquals(CIPHER_URL, options.getRemoteCipherUrl(),
            "Remote cipher must be applied even when OAuth is disabled - signature "
          + "deciphering is independent of authentication");
    }

    @Test
    @DisplayName("Remote cipher is configured when OAuth is enabled")
    void remoteCipherConfiguredWhenOAuthEnabled() throws Exception {
        YoutubeSourceOptions options = buildOptions(mockConfig(true, CIPHER_URL, ""));

        assertEquals(CIPHER_URL, options.getRemoteCipherUrl(),
            "Remote cipher should still be applied when OAuth is enabled");
    }

    @Test
    @DisplayName("Blank cipher URL leaves the local extractor in place")
    void blankUrlDisablesRemoteCipher() throws Exception {
        assertNull(buildOptions(mockConfig(false, "", "")).getRemoteCipherUrl(),
            "An empty URL should opt out of the remote cipher");
        assertNull(buildOptions(mockConfig(false, "   ", "")).getRemoteCipherUrl(),
            "A whitespace-only URL should opt out of the remote cipher");
        assertNull(buildOptions(mockConfig(false, null, "")).getRemoteCipherUrl(),
            "A null URL should opt out of the remote cipher");
    }

    @Test
    @DisplayName("Blank password is passed as null, a real password is preserved")
    void passwordHandling() throws Exception {
        // youtube-source treats an empty password as a real credential and would send
        // it to the cipher server, so blank must be normalised to null.
        assertNull(buildOptions(mockConfig(false, CIPHER_URL, "")).getRemoteCipherPassword(),
            "An empty password should be passed as null");
        assertNull(buildOptions(mockConfig(false, CIPHER_URL, "  ")).getRemoteCipherPassword(),
            "A whitespace-only password should be passed as null");
        assertEquals("hunter2",
            buildOptions(mockConfig(false, CIPHER_URL, "hunter2")).getRemoteCipherPassword(),
            "A configured password should be passed through unchanged");
    }

    @Test
    @DisplayName("Surrounding whitespace is trimmed from the cipher URL")
    void urlIsTrimmed() throws Exception {
        assertEquals(CIPHER_URL,
            buildOptions(mockConfig(false, "  " + CIPHER_URL + "  ")).getRemoteCipherUrl(),
            "A URL copied from config with stray whitespace should still work");
    }

    private static YoutubeSourceOptions buildOptions(BotConfig config) throws Exception {
        Method method = com.jagrosh.jmusicbot.audio.AudioSource.class
                .getDeclaredMethod("buildYoutubeOptions", BotConfig.class);
        method.setAccessible(true);
        return (YoutubeSourceOptions) method.invoke(null, config);
    }

    private static BotConfig mockConfig(boolean useOauth, String cipherUrl) {
        return mockConfig(useOauth, cipherUrl, "");
    }

    private static BotConfig mockConfig(boolean useOauth, String cipherUrl, String password) {
        BotConfig config = mock(BotConfig.class);
        when(config.useYouTubeOauth()).thenReturn(useOauth);
        when(config.getYoutubeRemoteCipherUrl()).thenReturn(cipherUrl);
        when(config.getYoutubeRemoteCipherPassword()).thenReturn(password);
        return config;
    }
}
