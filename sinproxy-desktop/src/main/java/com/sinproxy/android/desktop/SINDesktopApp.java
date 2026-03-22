package com.sinproxy.android.desktop;

import com.formdev.flatlaf.FlatDarkLaf;
import com.sinproxy.android.SINProxyConfig;
import com.sinproxy.android.core.SINProxyServer;
import com.sinproxy.android.utils.AssetManager;
import com.sinproxy.android.utils.SINLog;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

/**
 * Modern, Premium Desktop Application for SINproxy.
 * Features a Dark Mode GUI and automated VPN dependency management.
 */
public class SINDesktopApp extends JFrame {
    private static SINProxyServer proxyServer;
    private static Process tun2socksProcess;

    private JTextArea logArea;
    private JButton connectBtn;
    private JLabel statusLabel;
    private boolean isConnected = false;

    public SINDesktopApp() {
        setTitle("SINproxy v" + getVersion());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(500, 450);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // 1. Initialize Look and Feel
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception e) {
            SINLog.e("Failed to initialize FlatDarkLaf", e);
        }

        // 2. UI Components
        initUI();

        // 3. System Tray / Logging Integration
        SINLog.setListener(msg -> SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }));

        // 4. Shutdown Hook
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopAll();
            }
        });
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        mainPanel.setBackground(new Color(30, 31, 34));

        // Header
        JLabel titleLabel = new JLabel("SINproxy Server");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // Center Panel (Controls)
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridx = 0;

        statusLabel = new JLabel("Status: OFFLINE");
        statusLabel.setForeground(new Color(180, 180, 180));
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        centerPanel.add(statusLabel, gbc);

        connectBtn = new JButton("CONNECT");
        connectBtn.setPreferredSize(new Dimension(200, 50));
        connectBtn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        connectBtn.setBackground(new Color(0, 120, 212));
        connectBtn.setForeground(Color.WHITE);
        connectBtn.setFocusPainted(false);
        connectBtn.setBorder(BorderFactory.createEmptyBorder());
        connectBtn.addActionListener(e -> toggleConnection());
        gbc.gridy = 1;
        centerPanel.add(connectBtn, gbc);

        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // Log Console
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(new Color(20, 20, 22));
        logArea.setForeground(new Color(150, 200, 150));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setPreferredSize(new Dimension(0, 150));
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 63)));
        mainPanel.add(scrollPane, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private void toggleConnection() {
        if (!isConnected) {
            startAll();
        } else {
            stopAll();
        }
    }

    private void startAll() {
        new Thread(() -> {
            try {
                SINLog.i("Initializing connection...");
                
                // 1. Ensure VPN Assets (Windows Only)
                if (!AssetManager.ensureAssets()) {
                    SINLog.e("Missing required VPN binaries. Retrying...", null);
                }

                // 2. Start Proxy Server
                if (proxyServer == null) {
                    proxyServer = new SINProxyServer();
                }
                proxyServer.start();

                // 3. Configure System VPN/Proxy
                configureSystem(true);

                SwingUtilities.invokeLater(() -> {
                    isConnected = true;
                    connectBtn.setText("DISCONNECT");
                    connectBtn.setBackground(new Color(200, 50, 50));
                    statusLabel.setText("Status: CONNECTED (" + SINProxyConfig.BUG_SNI + ")");
                    statusLabel.setForeground(new Color(100, 255, 100));
                });

            } catch (Exception e) {
                SINLog.e("Failed to connect", e);
            }
        }).start();
    }

    private void stopAll() {
        SINLog.i("Closing connection...");
        configureSystem(false);
        if (proxyServer != null) {
            proxyServer.stop();
        }
        
        isConnected = false;
        connectBtn.setText("CONNECT");
        connectBtn.setBackground(new Color(0, 120, 212));
        statusLabel.setText("Status: OFFLINE");
        statusLabel.setForeground(new Color(180, 180, 180));
    }

    private void configureSystem(boolean enable) {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            if (new java.io.File("tun2socks.exe").exists()) {
                if (enable) startTun2Socks(); else stopTun2Socks();
            } else {
                setWindowsProxy(enable);
            }
        } else if (os.contains("nix") || os.contains("nux")) {
            setLinuxProxy(enable);
        }
    }

    private void startTun2Socks() {
        try {
            String cmd = "tun2socks.exe -proxy socks5://127.0.0.1:" + SINProxyConfig.SOCKS_PORT + " -device wintun";
            tun2socksProcess = Runtime.getRuntime().exec(cmd);
            SINLog.i("Real VPN Mode Active (tun2socks).");
        } catch (IOException e) {
            SINLog.e("VPN mode failed", e);
        }
    }

    private void stopTun2Socks() {
        if (tun2socksProcess != null) {
            tun2socksProcess.destroy();
            SINLog.i("VPN Mode stopping.");
        }
    }

    private void setWindowsProxy(boolean enable) {
        try {
            String proxy = "127.0.0.1:" + SINProxyConfig.DEFAULT_PORT;
            if (enable) {
                runCommand("reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" /v ProxyEnable /t REG_DWORD /d 1 /f");
                runCommand("reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" /v ProxyServer /t REG_SZ /d \"" + proxy + "\" /f");
                SINLog.i("Windows Proxy Enabled.");
            } else {
                runCommand("reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" /v ProxyEnable /t REG_DWORD /d 0 /f");
                SINLog.i("Windows Proxy Disabled.");
            }
        } catch (Exception e) {}
    }

    private void setLinuxProxy(boolean enable) {
        try {
            if (enable) {
                runCommand("gsettings set org.gnome.system.proxy mode 'manual'");
                runCommand("gsettings set org.gnome.system.proxy.http host '127.0.0.1'");
                runCommand("gsettings set org.gnome.system.proxy.http port " + SINProxyConfig.DEFAULT_PORT);
                SINLog.i("Linux Proxy Enabled.");
            } else {
                runCommand("gsettings set org.gnome.system.proxy mode 'none'");
                SINLog.i("Linux Proxy Disabled.");
            }
        } catch (Exception e) {}
    }

    private void runCommand(String cmd) throws Exception {
        Process p = Runtime.getRuntime().exec(cmd);
        p.waitFor();
    }

    private String getVersion() {
        return "1.0.1"; // Updated by CI
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SINDesktopApp app = new SINDesktopApp();
            app.setVisible(true);
        });
    }
}
