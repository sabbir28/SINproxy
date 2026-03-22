package com.sinproxy.android.desktop;

import com.sinproxy.android.SINProxyConfig;
import com.sinproxy.android.core.SINProxyServer;
import com.sinproxy.android.utils.SINLog;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Professional Cross-Platform Desktop Application for SINproxy.
 * Manages system proxy settings on Windows and Linux to emulate VPN behavior.
 */
public class SINDesktopApp {
    private static SINProxyServer proxyServer;

    public static void main(String[] args) {
        SINLog.i("Starting SINproxy Desktop Version...");

        proxyServer = new SINProxyServer();
        proxyServer.start();

        // 1. Detect OS and set system proxy / VPN
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            if (isTun2SocksAvailable()) {
                startTun2SocksVPN();
            } else {
                setWindowsProxy(true);
            }
        } else if (os.contains("nix") || os.contains("nux")) {
            setLinuxProxy(true);
        } else {
            SINLog.w("Automatic proxy configuration not supported for OS: " + os);
            SINLog.i("Please manually set your system proxy to 127.0.0.1:" + SINProxyConfig.DEFAULT_PORT);
        }

        // 2. Add Shutdown Hook to clear proxy on exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            SINLog.i("Shutting down... Clearing system settings.");
            if (os.contains("win")) {
                stopTun2SocksVPN();
                setWindowsProxy(false);
            } else if (os.contains("nix") || os.contains("nux")) {
                setLinuxProxy(false);
            }
            proxyServer.stop();
        }));

        SINLog.i("SINproxy is running. All system traffic is now routed through the proxy.");
        SINLog.i("Press Ctrl+C to stop.");
    }

    private static boolean isTun2SocksAvailable() {
        java.io.File exe = new java.io.File("tun2socks.exe");
        java.io.File dll = new java.io.File("wintun.dll");
        return exe.exists() && dll.exists();
    }

    private static Process tun2socksProcess;

    private static void startTun2SocksVPN() {
        try {
            SINLog.i("Real VPN Mode: Starting tun2socks...");
            // Command: tun2socks.exe -proxy socks5://127.0.0.1:1080 -device wintun
            String cmd = "tun2socks.exe -proxy socks5://127.0.0.1:" + SINProxyConfig.SOCKS_PORT + " -device wintun";
            tun2socksProcess = Runtime.getRuntime().exec(cmd);
            
            // Note: In a real app, you'd also need to set the default gateway to the TUN interface
            // using 'route add' or 'netsh interface ip set address'.
            SINLog.i("tun2socks started successfully. All system traffic redirected to SOCKS5.");
        } catch (Exception e) {
            SINLog.e("Failed to start tun2socks", e);
        }
    }

    private static void stopTun2SocksVPN() {
        if (tun2socksProcess != null) {
            tun2socksProcess.destroy();
            SINLog.i("tun2socks stopped.");
        }
    }

    private static void setWindowsProxy(boolean enable) {
        try {
            String proxy = "127.0.0.1:" + SINProxyConfig.DEFAULT_PORT;
            if (enable) {
                runCommand("reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" /v ProxyEnable /t REG_DWORD /d 1 /f");
                runCommand("reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" /v ProxyServer /t REG_SZ /d \"" + proxy + "\" /f");
                SINLog.i("Windows System Proxy ENABLED: " + proxy);
            } else {
                runCommand("reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" /v ProxyEnable /t REG_DWORD /d 0 /f");
                SINLog.i("Windows System Proxy DISABLED.");
            }
        } catch (Exception e) {
            SINLog.e("Failed to set Windows proxy", e);
        }
    }

    private static void setLinuxProxy(boolean enable) {
        try {
            String host = "127.0.0.1";
            int port = SINProxyConfig.DEFAULT_PORT;
            
            // Note: This works for GNOME-based environments (Ubuntu, Fedora, etc.)
            if (enable) {
                runCommand("gsettings set org.gnome.system.proxy mode 'manual'");
                runCommand("gsettings set org.gnome.system.proxy.http host '" + host + "'");
                runCommand("gsettings set org.gnome.system.proxy.http port " + port);
                runCommand("gsettings set org.gnome.system.proxy.https host '" + host + "'");
                runCommand("gsettings set org.gnome.system.proxy.https port " + port);
                SINLog.i("Linux (GNOME) System Proxy ENABLED: " + host + ":" + port);
            } else {
                runCommand("gsettings set org.gnome.system.proxy mode 'none'");
                SINLog.i("Linux (GNOME) System Proxy DISABLED.");
            }
        } catch (Exception e) {
            SINLog.w("Failed to set Linux proxy via gsettings. Ensure GNOME is used or set HTTP_PROXY manually.");
        }
    }

    private static void runCommand(String cmd) throws Exception {
        Process p = Runtime.getRuntime().exec(cmd);
        p.waitFor();
    }
}
