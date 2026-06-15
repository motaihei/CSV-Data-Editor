package com.example.csveditor.ui;

import org.junit.Test;

import java.util.List;
import java.util.prefs.Preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MainFrameTest {

    @Test
    public void splitPreferenceValueKeepsChunksUnderPreferencesLimit() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 15000; i++) {
            builder.append((char) ('a' + (i % 26)));
        }
        String value = builder.toString();

        List<String> chunks = MainFrame.splitPreferenceValue(value);

        assertTrue(chunks.size() > 1);
        StringBuilder restored = new StringBuilder();
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= Preferences.MAX_VALUE_LENGTH);
            restored.append(chunk);
        }
        assertEquals(value, restored.toString());
    }
}
