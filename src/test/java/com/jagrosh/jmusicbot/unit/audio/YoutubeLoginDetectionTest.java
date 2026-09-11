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
package com.jagrosh.jmusicbot.unit.audio;

import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for detecting "YouTube wants a signed-in session" playback failures.
 *
 * <p>Two independent defects stopped the OAuth hint in {@code onTrackException} from ever
 * firing, so operators only ever saw a raw stack trace with no indication of the remedy:
 *
 * <ol>
 *   <li>The check tested {@code exception.getMessage()} with {@code equals}, but when every
 *       YouTube client fails youtube-source raises an {@code AllClientsFailedException}
 *       whose message is only "All clients failed to load the item." The per-client reasons
 *       hang off it as causes and suppressed exceptions.</li>
 *   <li>YouTube writes "you’re" with a typographic apostrophe (U+2019), while the code
 *       compared against an ASCII {@code '}, so even a direct match failed.</li>
 * </ol>
 *
 * @see com.jagrosh.jmusicbot.audio.AudioHandler
 */
@DisplayName("YouTube Login Detection Tests")
class YoutubeLoginDetectionTest {

    @Test
    @DisplayName("Detects the typographic apostrophe YouTube actually sends - regression test")
    void detectsTypographicApostrophe() throws Exception {
        // U+2019, exactly as it arrives from YouTube.
        assertTrue(requiresLogin(new RuntimeException("Sign in to confirm you’re not a bot")),
            "The bot-check message uses U+2019, not an ASCII apostrophe");
    }

    @Test
    @DisplayName("Detects an ASCII apostrophe too")
    void detectsAsciiApostrophe() throws Exception {
        assertTrue(requiresLogin(new RuntimeException("Sign in to confirm you're not a bot")),
            "An ASCII apostrophe should still match");
    }

    @Test
    @DisplayName("Detects login markers nested in suppressed client failures - regression test")
    void detectsMarkerInSuppressedExceptions() throws Exception {
        // Mirrors the real shape: an aggregate wrapper whose own message says nothing
        // actionable, carrying the per-client reasons as suppressed exceptions.
        FriendlyException aggregate = new FriendlyException(
            "(yts.version: 1.18.1) All clients failed to load the item.",
            FriendlyException.Severity.COMMON, null);
        aggregate.addSuppressed(new RuntimeException("Client [ANDROID_VR] failed: This video requires login."));
        aggregate.addSuppressed(new RuntimeException("Client [WEB] failed: No supported audio streams available"));
        aggregate.addSuppressed(new RuntimeException(
            "Client [TVHTML5_SIMPLY] failed: Sign in to confirm you’re not a bot"));

        assertTrue(requiresLogin(aggregate),
            "The hint must fire when the reason is only present in suppressed client failures");
    }

    @Test
    @DisplayName("Detects login markers nested in the cause chain")
    void detectsMarkerInCauseChain() throws Exception {
        Throwable root = new RuntimeException("This video requires login.");
        Throwable middle = new RuntimeException("player response failure", root);
        FriendlyException top = new FriendlyException(
            "Something broke", FriendlyException.Severity.SUSPICIOUS, middle);

        assertTrue(requiresLogin(top), "The hint must follow the cause chain");
    }

    @Test
    @DisplayName("Unrelated playback failures are not misreported as login problems")
    void ignoresUnrelatedFailures() throws Exception {
        assertFalse(requiresLogin(new RuntimeException("Connection reset by peer")),
            "A network error is not a sign-in problem");

        FriendlyException cipherFailure = new FriendlyException(
            "All clients failed to load the item.", FriendlyException.Severity.COMMON, null);
        cipherFailure.addSuppressed(new RuntimeException(
            "Must find sig function from script: /s/player/8c3fda2d/base.js"));
        assertFalse(requiresLogin(cipherFailure),
            "A cipher extraction failure needs a cipher fix, not a sign-in");
    }

    @Test
    @DisplayName("Null and empty messages are handled without throwing")
    void handlesNullMessages() throws Exception {
        assertFalse(requiresLogin(new RuntimeException((String) null)),
            "A null message must not throw - the old code called equals() on it directly");
        assertFalse(requiresLogin(new RuntimeException("")), "An empty message should not match");
    }

    @Test
    @DisplayName("Self-referencing cause chains terminate instead of recursing forever")
    void handlesCyclicCauses() throws Exception {
        // Java forbids self-suppression, so build a real two-node cycle:
        // outer -> suppressed inner -> cause outer.
        RuntimeException outer = new RuntimeException("This video requires login.");
        RuntimeException inner = new RuntimeException("secondary client failure");
        outer.addSuppressed(inner);
        inner.initCause(outer);

        assertTrue(requiresLogin(outer), "Cycles must be visited once, not endlessly");
    }

    private static boolean requiresLogin(Throwable throwable) throws Exception {
        Method method = AudioHandler.class.getDeclaredMethod("requiresYoutubeLogin", Throwable.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, throwable);
    }
}
