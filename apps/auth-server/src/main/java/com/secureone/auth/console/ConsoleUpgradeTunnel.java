package com.secureone.auth.console;

import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.WebConnection;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Byte pipe for an upgraded connection (WebSocket) between the browser and the console, after
 * {@link ConsoleProxyFilter} has relayed the handshake. The console only uses one for live reload
 * while developing; production pages work without it.
 */
public class ConsoleUpgradeTunnel implements HttpUpgradeHandler {

    private Socket upstream;
    private InputStream upstreamIn;
    private final AtomicBoolean closed = new AtomicBoolean();
    private WebConnection connection;

    /** Called by the filter before the container hands over the connection. */
    void attach(Socket upstream, InputStream upstreamIn) {
        this.upstream = upstream;
        this.upstreamIn = upstreamIn;
    }

    @Override
    public void init(WebConnection webConnection) {
        this.connection = webConnection;
        try {
            InputStream clientIn = webConnection.getInputStream();
            OutputStream clientOut = webConnection.getOutputStream();
            OutputStream upstreamOut = upstream.getOutputStream();
            pump("console-ws-down", upstreamIn, clientOut);
            pump("console-ws-up", clientIn, upstreamOut);
        } catch (IOException e) {
            close();
        }
    }

    private void pump(String name, InputStream in, OutputStream out) {
        Thread thread = new Thread(() -> {
            byte[] buffer = new byte[8192];
            try {
                int read;
                while (!closed.get() && (read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    out.flush();
                }
            } catch (IOException | RuntimeException ignored) {
                // Either side went away.
            } finally {
                close();
            }
        }, name);
        thread.setDaemon(true);
        thread.start();
    }

    private void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        quietly(upstream);
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ignored) {
                // Already closed.
            }
        }
    }

    @Override
    public void destroy() {
        close();
    }

    private static void quietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Already closed.
        }
    }
}
