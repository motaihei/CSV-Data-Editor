package com.example.csveditor.app;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ShutdownRequestServerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void requestExistingShutdownInvokesRegisteredShutdownAction() throws Exception {
        String previousTempDir = System.getProperty("java.io.tmpdir");
        System.setProperty("java.io.tmpdir", temporaryFolder.getRoot().getAbsolutePath());
        CountDownLatch latch = new CountDownLatch(1);
        ShutdownRequestServer server = ShutdownRequestServer.start(new Runnable() {
            @Override
            public void run() {
                latch.countDown();
            }
        });
        try {
            assertEquals(0, ShutdownRequestServer.requestExistingShutdown());
            assertTrue(latch.await(3, TimeUnit.SECONDS));
        } finally {
            server.closeQuietly();
            if (previousTempDir == null) {
                System.clearProperty("java.io.tmpdir");
            } else {
                System.setProperty("java.io.tmpdir", previousTempDir);
            }
        }
    }
}
