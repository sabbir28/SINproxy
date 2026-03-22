package com.sinproxy.android.vpn;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import com.sinproxy.android.core.SINProxyServer;
import com.sinproxy.android.utils.SINLog;

import java.io.IOException;

/**
 * Professional VpnService implementation for SINproxy.
 * This class establishes a virtual network interface to capture device traffic.
 */
public class SINVpnService extends VpnService {
    private Thread mThread;
    private ParcelFileDescriptor mInterface;
    private SINProxyServer mProxyServer;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopVpn();
            return START_NOT_STICKY;
        }

        startVpn();
        return START_STICKY;
    }

    private void startVpn() {
        if (mThread != null) return;

        mThread = new Thread(() -> {
            try {
                // 1. Start the Proxy Server first
                mProxyServer = new SINProxyServer();
                mProxyServer.start();

                // 2. Configure the VPN Interface
                Builder builder = new Builder();
                builder.setSession("SINproxy VPN")
                       .addAddress("10.0.0.1", 24) // Virtual IP
                       .addRoute("0.0.0.0", 0)     // Capture all traffic
                       .addDnsServer("8.8.8.8")
                       .addDnsServer("1.1.1.1");
                
                // On Android 10+, we can set a system-wide proxy hint
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    // This tells apps to use our local proxy for HTTP/HTTPS
                    // Note: This requires the proxy to be listening on 127.0.0.1
                    // builder.setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", 8081));
                }

                mInterface = builder.establish();
                SINLog.i("VPN Interface established correctly.");

                // 3. Keep-alive loop if needed for manual packet processing
                // For a professional implementation, one would use a TUN-to-SOCKS/Proxy bridge here.
                while (mProxyServer.isRunning()) {
                    Thread.sleep(1000);
                }

            } catch (Exception e) {
                SINLog.e("VPN start error", e);
            } finally {
                stopVpn();
            }
        }, "SINproxy-VPN-Thread");

        mThread.start();
    }

    private void stopVpn() {
        if (mProxyServer != null) mProxyServer.stop();
        if (mInterface != null) {
            try {
                mInterface.close();
            } catch (IOException ignored) {}
            mInterface = null;
        }
        if (mThread != null) {
            mThread.interrupt();
            mThread = null;
        }
        stopSelf();
        SINLog.i("VPN Service stopped.");
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }
}
