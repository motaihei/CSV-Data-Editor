package com.example.csveditor.app;

import javax.swing.SwingUtilities;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;
import java.util.Properties;

/**
 * Loopback-only shutdown request channel for the launcher script.
 */
final class ShutdownRequestServer implements Closeable {

    static final String SHUTDOWN_ARGUMENT = "--shutdown-existing";
    private static final String CONTROL_FILE_NAME = "csv-data-editor.shutdown";
    private static final String REQUEST_PREFIX = "shutdown ";
    private static final int CONNECT_TIMEOUT_MILLIS = 1200;
    private static final int ACCEPT_TIMEOUT_MILLIS = 500;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ServerSocket serverSocket;
    private final File controlFile;
    private final String token;
    private volatile boolean closed;

    private ShutdownRequestServer(ServerSocket serverSocket, File controlFile, String token) {
        this.serverSocket = serverSocket;
        this.controlFile = controlFile;
        this.token = token;
    }

    static ShutdownRequestServer start(Runnable shutdownAction) throws IOException {
        ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        socket.setSoTimeout(ACCEPT_TIMEOUT_MILLIS);

        String token = createToken();
        ShutdownRequestServer server = new ShutdownRequestServer(socket, defaultControlFile(), token);
        server.writeControlFile();
        Thread thread = new Thread(new ServerTask(server, shutdownAction), "csv-editor-shutdown-request");
        thread.setDaemon(true);
        thread.start();
        return server;
    }

    static int requestExistingShutdown() {
        File controlFile = defaultControlFile();
        if (!controlFile.isFile()) {
            return 2;
        }

        Properties properties = new Properties();
        try {
            FileInputStream input = new FileInputStream(controlFile);
            try {
                properties.load(input);
            } finally {
                input.close();
            }

            int port = Integer.parseInt(properties.getProperty("port", ""));
            String token = properties.getProperty("token", "");
            if (token.length() == 0) {
                return 2;
            }

            Socket socket = new Socket();
            try {
                socket.connect(new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), port),
                        CONNECT_TIMEOUT_MILLIS);
                socket.setSoTimeout(CONNECT_TIMEOUT_MILLIS);
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                writer.write(REQUEST_PREFIX);
                writer.write(token);
                writer.newLine();
                writer.flush();
                String response = reader.readLine();
                return "OK".equals(response) ? 0 : 3;
            } finally {
                socket.close();
            }
        } catch (Exception ex) {
            return 3;
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
        IOException failure = null;
        try {
            serverSocket.close();
        } catch (IOException ex) {
            failure = ex;
        }
        if (controlFile.exists() && !controlFile.delete() && failure == null) {
            failure = new IOException("Could not delete shutdown control file: " + controlFile.getAbsolutePath());
        }
        if (failure != null) {
            throw failure;
        }
    }

    void closeQuietly() {
        try {
            close();
        } catch (IOException ignored) {
            // Nothing useful can be reported while the JVM is shutting down.
        }
    }

    private void writeControlFile() throws IOException {
        File parent = controlFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create shutdown control directory: " + parent.getAbsolutePath());
        }

        Properties properties = new Properties();
        properties.setProperty("port", Integer.toString(serverSocket.getLocalPort()));
        properties.setProperty("token", token);
        FileOutputStream output = new FileOutputStream(controlFile);
        try {
            properties.store(output, "CSV Data Editor shutdown channel");
        } finally {
            output.close();
        }
    }

    private void handle(Socket socket, Runnable shutdownAction) throws IOException {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
            String request = reader.readLine();
            if ((REQUEST_PREFIX + token).equals(request)) {
                SwingUtilities.invokeLater(shutdownAction);
                writer.write("OK");
            } else {
                writer.write("DENIED");
            }
            writer.newLine();
            writer.flush();
        } finally {
            socket.close();
        }
    }

    private static File defaultControlFile() {
        String tempDir = System.getProperty("java.io.tmpdir");
        String userName = System.getProperty("user.name", "user");
        File dir = new File(tempDir, "csv-data-editor");
        return new File(dir, sanitizeFileNamePart(userName) + "-" + CONTROL_FILE_NAME);
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

    private static String createToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
    }

    private boolean isClosed() {
        return closed;
    }

    private static final class ServerTask implements Runnable {
        private final ShutdownRequestServer server;
        private final Runnable shutdownAction;

        private ServerTask(ShutdownRequestServer server, Runnable shutdownAction) {
            this.server = server;
            this.shutdownAction = shutdownAction;
        }

        @Override
        public void run() {
            while (!server.isClosed()) {
                try {
                    server.handle(server.serverSocket.accept(), shutdownAction);
                } catch (SocketTimeoutException ignored) {
                    // Allows the thread to observe close requests promptly.
                } catch (IOException ex) {
                    if (!server.isClosed()) {
                        break;
                    }
                }
            }
        }
    }
}
