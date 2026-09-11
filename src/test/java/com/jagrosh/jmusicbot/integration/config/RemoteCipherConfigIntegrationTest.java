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
package com.jagrosh.jmusicbot.integration.config;

import com.jagrosh.jmusicbot.BaseConfigTest;
import com.jagrosh.jmusicbot.config.loader.ConfigLoader;
import com.jagrosh.jmusicbot.config.model.ConfigOption;
import com.typesafe.config.Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the remote cipher settings travel the real config chain:
 * reference.conf defaults -> user overrides -> {@link ConfigOption} keys.
 *
 * <p>The unit tests around {@code buildYoutubeOptions} mock {@code BotConfig}, so a typo in
 * a config path would not show up there. These tests exercise the untyped key strings.
 */
@DisplayName("Remote Cipher Config Integration Tests")
class RemoteCipherConfigIntegrationTest extends BaseConfigTest {

    // meta.configVersion must match reference.conf. Without it the file is detected as a
    // legacy v0 config and migrated, and the legacy mapping drops keys it does not know.
    private static final String MINIMAL =
            "meta { configVersion = 2 }\ndiscord.token = test_token\ndiscord.owner = 123456789\n";

    @Test
    @DisplayName("A default install gets a working remote cipher without touching its config")
    void defaultsProvideRemoteCipher() throws IOException {
        Config merged = ConfigLoader.loadMergedConfig(createTempConfigFile(MINIMAL));

        assertTrue(merged.hasPath(ConfigOption.YOUTUBE_REMOTE_CIPHER_URL.getKey()),
            "reference.conf must define " + ConfigOption.YOUTUBE_REMOTE_CIPHER_URL.getKey());

        String url = ConfigOption.YOUTUBE_REMOTE_CIPHER_URL.getString(merged);
        assertFalse(url.isBlank(),
            "The shipped default must be a real cipher server - a blank default would leave "
          + "every install on the local extractor, which is the bug this setting fixes");
        assertTrue(url.startsWith("https://"), "The default cipher server should use HTTPS, got: " + url);
    }

    @Test
    @DisplayName("Password defaults to empty rather than missing")
    void passwordDefaultsToEmpty() throws IOException {
        Config merged = ConfigLoader.loadMergedConfig(createTempConfigFile(MINIMAL));

        assertTrue(merged.hasPath(ConfigOption.YOUTUBE_REMOTE_CIPHER_PASSWORD.getKey()),
            "reference.conf must define " + ConfigOption.YOUTUBE_REMOTE_CIPHER_PASSWORD.getKey());
        assertEquals("", ConfigOption.YOUTUBE_REMOTE_CIPHER_PASSWORD.getString(merged),
            "The public cipher server needs no password, so the default should be empty");
    }

    @Test
    @DisplayName("Users can point at a self-hosted cipher server")
    void userCanOverrideCipherServer() throws IOException {
        Path configFile = createTempConfigFile(MINIMAL
            + "playback.youtube.remoteCipher.url = \"https://cipher.example.internal/\"\n"
            + "playback.youtube.remoteCipher.password = \"s3cret\"\n");

        Config merged = ConfigLoader.loadMergedConfig(configFile);

        assertEquals("https://cipher.example.internal/",
            ConfigOption.YOUTUBE_REMOTE_CIPHER_URL.getString(merged));
        assertEquals("s3cret",
            ConfigOption.YOUTUBE_REMOTE_CIPHER_PASSWORD.getString(merged));
    }

    @Test
    @DisplayName("Users can opt out of the remote cipher entirely")
    void userCanDisableRemoteCipher() throws IOException {
        Path configFile = createTempConfigFile(MINIMAL
            + "playback.youtube.remoteCipher.url = \"\"\n");

        Config merged = ConfigLoader.loadMergedConfig(configFile);

        assertEquals("", ConfigOption.YOUTUBE_REMOTE_CIPHER_URL.getString(merged),
            "An empty URL should survive the merge so the local extractor can be chosen");
    }

    @Test
    @DisplayName("The remote cipher default is independent of the OAuth setting")
    void cipherIsIndependentOfOauth() throws IOException {
        // The original bug was exactly this coupling.
        Config oauthOff = ConfigLoader.loadMergedConfig(
            createTempConfigFile(MINIMAL + "playback.youtube.useOAuth = false\n"));
        Config oauthOn = ConfigLoader.loadMergedConfig(
            createTempConfigFile(MINIMAL + "playback.youtube.useOAuth = true\n"));

        assertEquals(ConfigOption.YOUTUBE_REMOTE_CIPHER_URL.getString(oauthOn),
                     ConfigOption.YOUTUBE_REMOTE_CIPHER_URL.getString(oauthOff),
            "The cipher server must not depend on whether OAuth is enabled");
    }
}
