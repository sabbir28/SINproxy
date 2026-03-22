package com.sinproxy.android.core;

import com.sinproxy.android.SINProxyConfig;
import com.sinproxy.android.security.CertificateHelper;
import com.sinproxy.android.utils.SINLog;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Handles individual client connections for SINproxy
 * Performs SNI Spoofing, MITM, and Header Injection
 */
public class ProxyHandler implements Runnable {
    private final Socket clientSocket;
    private final String sni;
    private Socket remoteSocket;
    private SSLSocket secureRemote;
    private SSLSocket secureClient;

    public ProxyHandler(Socket clientSocket, String sni) {
        this.clientSocket = clientSocket;
        this.sni = (sni != null && !sni.isEmpty()) ? sni : SINProxyConfig.BUG_SNI;
    }

    @Override
    public void run() {
        try {
            InputStream in = clientSocket.getInputStream();
            byte[] buffer = new byte[SINProxyConfig.BUFFER_SIZE];
            int read = in.read(buffer);
            if (read <= 0) return;

            String firstLine = new String(buffer, 0, read).split("\r\n")[0];
            SINLog.i("Handling Request: " + firstLine);

            if (firstLine.startsWith("CONNECT")) {
                handleConnect(firstLine);
            } else {
                SINLog.w("Plain HTTP not supported yet.");
                clientSocket.getOutputStream().write("HTTP/1.1 501 Not Implemented\r\n\r\n".getBytes());
            }

        } catch (Exception e) {
            SINLog.e("Connection handler error", e);
        } finally {
            cleanup();
        }
    }

    /**
     * Entry point for SOCKS5 or direct TCP interception
     */
    public void handleDirectConnect(String host, int port) {
        try {
            establishTunnel(host, port);
        } catch (Exception e) {
            SINLog.e("Direct connection error for " + host, e);
        } finally {
            cleanup();
        }
    }

    private void handleConnect(String firstLine) throws Exception {
        String[] parts = firstLine.split(" ");
        if (parts.length < 2) return;
        
        String hostPort = parts[1];
        String host;
        int port = 443;
        if (hostPort.contains(":")) {
            String[] hp = hostPort.split(":");
            host = hp[0];
            port = Integer.parseInt(hp[1]);
        } else {
            host = hostPort;
        }

        // Send 200 OK for HTTP CONNECT before starting TLS
        clientSocket.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
        clientSocket.getOutputStream().flush();

        establishTunnel(host, port);
    }

    private void establishTunnel(String host, int port) throws Exception {
        SINLog.i("Establishing Tunnel → " + host + ":" + port + " (SNI: " + sni + ")");

        // 1. Connect to Outbound Server with SNI Spoofing
        remoteSocket = new Socket();
        remoteSocket.connect(new InetSocketAddress(host, port), SINProxyConfig.PROXY_TIMEOUT);

        SSLContext remoteCtx = SSLContext.getInstance("TLS");
        remoteCtx.init(null, new TrustManager[]{new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        }}, null);

        SSLSocketFactory factory = remoteCtx.getSocketFactory();
        secureRemote = (SSLSocket) factory.createSocket(remoteSocket, sni, port, true);
        
        try {
            Method setHostnameMethod = secureRemote.getClass().getMethod("setHostname", String.class);
            setHostnameMethod.invoke(secureRemote, sni);
        } catch (Exception ignored) {}

        secureRemote.startHandshake();
        SINLog.i("Outbound TLS established: " + secureRemote.getSession().getProtocol());

        // 2. Wrap Inbound Client with MITM TLS
        KeyPair keyPair = CertificateHelper.generateKeyPair();
        X509Certificate cert = CertificateHelper.generateHostCertificate(host, keyPair.getPublic());
        
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        ks.setKeyEntry("key", keyPair.getPrivate(), "password".toCharArray(), new Certificate[]{cert, CertificateHelper.getRootCA()});

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, "password".toCharArray());

        SSLContext clientCtx = SSLContext.getInstance("TLS");
        clientCtx.init(kmf.getKeyManagers(), null, null);

        secureClient = (SSLSocket) clientCtx.getSocketFactory().createSocket(clientSocket, null, clientSocket.getPort(), true);
        secureClient.setUseClientMode(false);
        secureClient.startHandshake();
        SINLog.i("Inbound MITM TLS established for " + host);

        // 3. Bidirectional Forwarding
        startForwarding(secureClient, secureRemote);
    }

    private void startForwarding(SSLSocket client, SSLSocket remote) {
        Thread t1 = new Thread(() -> bridge(client, remote, "client -> server"));
        Thread t2 = new Thread(() -> bridge(remote, client, "server -> client"));
        t1.start();
        t2.start();
        try {
            t1.join();
            t2.join();
        } catch (InterruptedException ignored) {}
    }

    private void bridge(SSLSocket src, SSLSocket dst, String label) {
        try {
            InputStream in = src.getInputStream();
            OutputStream out = dst.getOutputStream();
            byte[] buffer = new byte[SINProxyConfig.BUFFER_SIZE];
            int read;

            // Handle first request for header injection if it's the client->server bridge
            if (label.equals("client -> server")) {
                read = in.read(buffer);
                if (read > 0) {
                    byte[] modified = modifyHeaders(Arrays.copyOf(buffer, read));
                    out.write(modified);
                    out.flush();
                }
            }

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                out.flush();
            }
        } catch (Exception e) {
            SINLog.d("Connection bridge closed (" + label + "): " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private byte[] modifyHeaders(byte[] data) {
        try {
            String content = new String(data);
            if (!content.contains("\r\n\r\n")) return data;

            String[] sections = content.split("\r\n\r\n", 2);
            String headers = sections[0];
            String body = (sections.length > 1) ? sections[1] : "";

            StringBuilder newHeaders = new StringBuilder();
            String[] lines = headers.split("\r\n");
            newHeaders.append(lines[0]).append("\r\n"); // Keep GET/POST line

            // Inject headers
            newHeaders.append("X-Online-Host: ").append(sni).append("\r\n");
            newHeaders.append("X-Forwarded-Host: ").append(sni).append("\r\n");

            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].toLowerCase();
                if (line.startsWith("x-online-host:") || line.startsWith("x-forwarded-host:")) continue;
                newHeaders.append(lines[i]).append("\r\n");
            }

            return (newHeaders.toString() + "\r\n" + body).getBytes();
        } catch (Exception e) {
            return data;
        }
    }

    private void cleanup() {
        try { if (clientSocket != null) clientSocket.close(); } catch (Exception ignored) {}
        try { if (remoteSocket != null) remoteSocket.close(); } catch (Exception ignored) {}
        try { if (secureRemote != null) secureRemote.close(); } catch (Exception ignored) {}
        try { if (secureClient != null) secureClient.close(); } catch (Exception ignored) {}
    }
}
