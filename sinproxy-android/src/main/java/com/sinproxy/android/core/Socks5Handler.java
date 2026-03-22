package com.sinproxy.android.core;

import com.sinproxy.android.SINProxyConfig;
import com.sinproxy.android.utils.SINLog;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;

/**
 * Basic SOCKS5 Protocol Handler for SINproxy.
 * Bridges SOCKS5 CONNECT requests to the internal ProxyHandler logic.
 */
public class Socks5Handler implements Runnable {
    private final Socket clientSocket;
    private final String sni;

    public Socks5Handler(Socket clientSocket, String sni) {
        this.clientSocket = clientSocket;
        this.sni = sni;
    }

    @Override
    public void run() {
        try {
            InputStream in = clientSocket.getInputStream();
            OutputStream out = clientSocket.getOutputStream();

            // 1. SOCKS5 Handshake (Method Selection)
            // [VER, NMETHODS, METHODS]
            byte[] header = new byte[2];
            if (in.read(header) < 2) return;
            if (header[0] != 0x05) return; // Only SOCKS5

            int nMethods = header[1] & 0xFF;
            byte[] methods = new byte[nMethods];
            in.read(methods);

            // Respond: No Authentication Required
            out.write(new byte[]{0x05, 0x00});
            out.flush();

            // 2. SOCKS5 Request
            // [VER, CMD, RSV, ATYP, DST.ADDR, DST.PORT]
            byte[] requestHeader = new byte[4];
            if (in.read(requestHeader) < 4) return;
            if (requestHeader[1] != 0x01) { // 0x01 = CONNECT
                out.write(new byte[]{0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0}); // Command not supported
                return;
            }

            String host = "";
            int port = 0;
            int atyp = requestHeader[3] & 0xFF;

            if (atyp == 0x01) { // IPv4
                byte[] addr = new byte[4];
                in.read(addr);
                host = (addr[0] & 0xFF) + "." + (addr[1] & 0xFF) + "." + (addr[2] & 0xFF) + "." + (addr[3] & 0xFF);
            } else if (atyp == 0x03) { // Domain Name
                int len = in.read() & 0xFF;
                byte[] addr = new byte[len];
                in.read(addr);
                host = new String(addr);
            } else if (atyp == 0x04) { // IPv6
                byte[] addr = new byte[16];
                in.read(addr);
                // Simple representation
                host = "IPv6[" + addr.length + " bytes]";
            }

            byte[] portBytes = new byte[2];
            in.read(portBytes);
            port = ((portBytes[0] & 0xFF) << 8) | (portBytes[1] & 0xFF);

            SINLog.i("SOCKS5 CONNECT → " + host + ":" + port);

            // Respond: Success
            out.write(new byte[]{0x05, 0x00, 0x00, 0x01, 127, 0, 0, 1, (byte)((port >> 8) & 0xFF), (byte)(port & 0xFF)});
            out.flush();

            // 3. Hand over to ProxyHandler logic (MITM/SNI)
            // Note: Since SOCKS5 is already a tunnel, we don't receive a "CONNECT" HTTP line.
            // We pass a dummy first line to ProxyHandler or modify ProxyHandler to handle direct sockets.
            
            // For simplicity, we'll implement the bridge logic directly here or use a helper.
            ProxyHandler handler = new ProxyHandler(clientSocket, sni);
            handler.handleDirectConnect(host, port);

        } catch (Exception e) {
            SINLog.e("SOCKS5 Error", e);
        } finally {
            cleanup();
        }
    }

    private void cleanup() {
        try { if (clientSocket != null) clientSocket.close(); } catch (Exception ignored) {}
    }
}
