package com.sinproxy.android.core;

import com.sinproxy.android.SINProxyConfig;
import com.sinproxy.android.security.CertificateHelper;
import com.sinproxy.android.utils.SINLog;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main Proxy Server for SINproxy Android
 */
public class SINProxyServer {
    private final String host;
    private final int port;
    private final String sni;
    private boolean running = false;
    private ServerSocket serverSocket;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public SINProxyServer() {
        this(SINProxyConfig.DEFAULT_HOST, SINProxyConfig.DEFAULT_PORT, SINProxyConfig.BUG_SNI);
    }

    public SINProxyServer(String host, int port, String sni) {
        this.host = host;
        this.port = port;
        this.sni = sni;
    }

    public void start() {
        new Thread(() -> {
            try {
                CertificateHelper.ensureCA();
                
                // HTTP Proxy Listener
                serverSocket = new ServerSocket(port);
                new Thread(() -> startSocks5Listener(), "Socks5-Listener").start();
                
                running = true;
                SINLog.i("SINproxy Java Professional Server started on " + host + ":" + port);
                SINLog.i("SOCKS5 Listener active on port " + SINProxyConfig.SOCKS_PORT);
                SINLog.i("Tunneling via SNI: " + sni);

                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        executor.execute(new ProxyHandler(clientSocket, sni));
                    } catch (IOException e) {
                        if (running) SINLog.e("Accept error", e);
                    }
                }
            } catch (Exception e) {
                SINLog.e("Failed to start SINproxy server", e);
            } finally {
                stop();
            }
        }).start();
    }

    private void startSocks5Listener() {
        try (ServerSocket socksSocket = new ServerSocket(SINProxyConfig.SOCKS_PORT)) {
            while (running) {
                try {
                    Socket clientSocket = socksSocket.accept();
                    executor.execute(new Socks5Handler(clientSocket, sni));
                } catch (IOException e) {
                    if (running) SINLog.e("SOCKS5 Accept error", e);
                }
            }
        } catch (IOException e) {
            SINLog.e("Failed to start SOCKS5 server", e);
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
        executor.shutdownNow();
        SINLog.i("Proxy server stopped.");
    }

    public boolean isRunning() {
        return running;
    }
}
