package com.example.csveditor.app;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;

/**
 * Holds the process-wide lock used to prevent multiple application instances.
 */
public final class SingleInstanceLock implements Closeable {

    private static final String LOCK_FILE_NAME = "csv-data-editor.lock";

    private final RandomAccessFile lockAccessFile;
    private final FileChannel channel;
    private final FileLock lock;
    private boolean closed;

    private SingleInstanceLock(RandomAccessFile lockAccessFile, FileChannel channel, FileLock lock) {
        this.lockAccessFile = lockAccessFile;
        this.channel = channel;
        this.lock = lock;
    }

    public static SingleInstanceLock acquire() throws IOException {
        return acquire(defaultLockFile().toPath());
    }

    static SingleInstanceLock acquire(Path lockFile) throws IOException {
        return tryAcquire(lockFile.toFile());
    }

    static SingleInstanceLock tryAcquire(File lockFile) throws IOException {
        File parent = lockFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create lock directory: " + parent.getAbsolutePath());
        }

        RandomAccessFile lockAccessFile = new RandomAccessFile(lockFile, "rw");
        FileChannel channel = lockAccessFile.getChannel();
        FileLock lock = null;
        try {
            lock = channel.tryLock();
            if (lock == null) {
                closeQuietly(channel);
                closeQuietly(lockAccessFile);
                return null;
            }
            return new SingleInstanceLock(lockAccessFile, channel, lock);
        } catch (OverlappingFileLockException ex) {
            closeQuietly(channel);
            closeQuietly(lockAccessFile);
            return null;
        } catch (IOException ex) {
            closeQuietly(channel);
            closeQuietly(lockAccessFile);
            throw ex;
        }
    }

    static File defaultLockFile() {
        String tempDir = System.getProperty("java.io.tmpdir");
        String userName = System.getProperty("user.name", "user");
        File lockDir = new File(tempDir, "csv-data-editor");
        return new File(lockDir, sanitizeFileNamePart(userName) + "-" + LOCK_FILE_NAME);
    }

    private static String sanitizeFileNamePart(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch == '.' || ch == '-' || ch == '_') {
                builder.append(ch);
            } else {
                builder.append('_');
            }
        }
        return builder.length() == 0 ? "user" : builder.toString();
    }

    public void closeQuietly() {
        try {
            close();
        } catch (IOException ignored) {
            // Nothing useful can be reported while the JVM is shutting down.
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;

        IOException failure = null;
        try {
            lock.release();
        } catch (IOException ex) {
            failure = ex;
        }

        try {
            channel.close();
        } catch (IOException ex) {
            if (failure == null) {
                failure = ex;
            }
        }

        try {
            lockAccessFile.close();
        } catch (IOException ex) {
            if (failure == null) {
                failure = ex;
            }
        }

        if (failure != null) {
            throw failure;
        }
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Ignore cleanup failures while abandoning a failed lock acquisition.
        }
    }
}
