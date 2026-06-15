package com.example.csveditor.app;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class SingleInstanceLockTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void acquireReturnsNullWhenLockIsAlreadyHeldInSameJvm() throws Exception {
        Path lockFile = temporaryFolder.newFile("app.lock").toPath();

        SingleInstanceLock firstLock = SingleInstanceLock.acquire(lockFile);
        try {
            assertNotNull(firstLock);

            SingleInstanceLock secondLock = SingleInstanceLock.acquire(lockFile);

            assertNull(secondLock);
        } finally {
            if (firstLock != null) {
                firstLock.close();
            }
        }
    }

    @Test
    public void acquireSucceedsAfterPreviousLockIsClosed() throws Exception {
        Path lockFile = temporaryFolder.newFile("app.lock").toPath();

        SingleInstanceLock firstLock = SingleInstanceLock.acquire(lockFile);
        assertNotNull(firstLock);
        firstLock.close();

        SingleInstanceLock secondLock = SingleInstanceLock.acquire(lockFile);
        try {
            assertNotNull(secondLock);
        } finally {
            if (secondLock != null) {
                secondLock.close();
            }
        }
    }
}
