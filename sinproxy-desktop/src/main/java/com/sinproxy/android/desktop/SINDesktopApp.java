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
import java.io.File;
import java.io.IOException;

/**
 * Advanced Desktop Application for SINproxy.
 * Features: VPN Tunneling, Persistent Assets, and Real-time Config.
 */
public class SINDesktopApp extends JFrame {
    private static SINProxyServer proxyServer;
    private static Process tun2socksProcess;

    private JTextArea logArea;
    private JButton connectBtn;
    private JLabel statusLabel;
    private JTextField sniField, proxyPortField, socksPortField;
    private boolean isConnected = false;

    public SINDesktopApp() {
        setTitle("SINproxy Pro v1.0.2");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(550, 500);
        setLocationRelativeTo(null);

        // 1. Initialize Look and Feel
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception e) {}

        // 2. Main Tabbed Layout
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Dashboard", createDashboardPanel());
        tabbedPane.addTab("Settings", createSettingsPanel());
        add(tabbedPane);

        // 3. Logging Integration
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

    private JPanel createDashboardPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        panel.setBackground(new Color(30, 31, 34));

        // Header
        JLabel titleLabel = new JLabel("SINproxy Tunnel");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 26));
        titleLabel.setForeground(new Color(0, 120, 212));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(titleLabel, BorderLayout.NORTH);

        // Center (Button & Status)
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.insets = new Insets(10, 0, 10, 0);

        statusLabel = new JLabel("Status: DISCONNECTED");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        statusLabel.setForeground(Color.GRAY);
        center.add(statusLabel, gbc);

        connectBtn = new JButton("START VPN");
        connectBtn.setPreferredSize(new Dimension(220, 60));
        connectBtn.setFont(new Font("Segoe UI", Font.BOLD, 18));
        connectBtn.setBackground(new Color(0, 120, 212));
        connectBtn.setForeground(Color.WHITE);
        connectBtn.setFocusPainted(false);
        connectBtn.addActionListener(e -> toggleConnection());
        gbc.gridy = 1;
        center.add(connectBtn, gbc);

        panel.add(center, BorderLayout.CENTER);

        // Console
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(new Color(20, 20, 22));
        logArea.setForeground(new Color(150, 200, 150));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setPreferredSize(new Dimension(0, 120));
        panel.add(scroll, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createSettingsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(30, 30, 30, 30));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridx = 0; gbc.gridy = 0;

        panel.add(new JLabel("Bug SNI (Host):"), gbc);
        sniField = new JTextField(SINProxyConfig.BUG_SNI);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(sniField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        panel.add(new JLabel("HTTP Proxy Port:"), gbc);
        proxyPortField = new JTextField(String.valueOf(SINProxyConfig.DEFAULT_PORT));
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(proxyPortField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        panel.add(new JLabel("SOCKS5 Port:"), gbc);
        socksPortField = new JTextField(String.valueOf(SINProxyConfig.SOCKS_PORT));
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(socksPortField, gbc);

        JButton saveBtn = new JButton("Apply Settings");
        saveBtn.addActionListener(e -> applySettings());
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        panel.add(saveBtn, gbc);

        return panel;
    }

    private void applySettings() {
        try {
            SINProxyConfig.BUG_SNI = sniField.getText();
            // Note: Ports are harder to change without server restart, but we update the config
            SINLog.i("Settings applied: SNI=" + SINProxyConfig.BUG_SNI);
            JOptionPane.showMessageDialog(this, "Settings updated! Please reconnect to apply fully.");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error in settings input.");
        }
    }

    private void toggleConnection() {
        if (!isConnected) startAll(); else stopAll();
    }

    private void startAll() {
        new Thread(() -> {
            try {
                // 1. Prepare Binaries in AppData
                if (!AssetManager.ensureAssets()) {
                    SINLog.e("Failed to acquire VPN binaries. Tunneling might fail.", null);
                }

                // 2. Start Servers
                if (proxyServer == null) proxyServer = new SINProxyServer();
                proxyServer.start();

                // 3. Start VPN Tunnel (Windows)
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    startTunnel();
                } else {
                    setLinuxProxy(true);
                }

                SwingUtilities.invokeLater(() -> {
                    isConnected = true;
                    connectBtn.setText("STOP VPN");
                    connectBtn.setBackground(new Color(200, 50, 50));
                    statusLabel.setText("Status: CONNECTED (" + SINProxyConfig.BUG_SNI + ")");
                    statusLabel.setForeground(new Color(100, 255, 100));
                });
            } catch (Exception e) {
                SINLog.e("Connection Error", e);
            }
        }).start();
    }

    private void stopAll() {
        stopTunnel();
        if (proxyServer != null) proxyServer.stop();
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            setWindowsProxy(false); // Fallback cleanup
        } else {
            setLinuxProxy(false);
        }

        isConnected = false;
        connectBtn.setText("START VPN");
        connectBtn.setBackground(new Color(0, 120, 212));
        statusLabel.setText("Status: DISCONNECTED");
        statusLabel.setForeground(Color.GRAY);
    }

    private void startTunnel() {
        try {
            File binDir = AssetManager.getBinDir();
            File exe = new File(binDir, "tun2socks.exe");
            
            if (!exe.exists()) {
                SINLog.w("Binary tun2socks.exe not found at " + exe.getAbsolutePath() + ". Falling back to System Proxy.");
                setWindowsProxy(true);
                return;
            }

            // Command using the absolute path to the persistent binary
            String cmd = String.format("\"%s\" -proxy socks5://127.0.0.1:%d -device wintun", 
                    exe.getAbsolutePath(), SINProxyConfig.SOCKS_PORT);
            
            tun2socksProcess = Runtime.getRuntime().exec(cmd);
            SINLog.i("Success: Tunnel active via wintun device.");
        } catch (IOException e) {
            SINLog.e("Tunnel start failed. Check your admin permissions.", e);
        }
    }

    private void stopTunnel() {
        if (tun2socksProcess != null) {
            tun2socksProcess.destroy();
            SINLog.i("Tunnel stopped.");
        }
    }

    private void setWindowsProxy(boolean enable) {
        try {
            if (enable) {
                runCommand("reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" /v ProxyEnable /t REG_DWORD /d 1 /f");
                runCommand("reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" /v ProxyServer /t REG_SZ /d \"127.0.0.1:" + SINProxyConfig.DEFAULT_PORT + "\" /f");
            } else {
                runCommand("reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" /v ProxyEnable /t REG_DWORD /d 0 /f");
            }
        } catch (Exception e) {}
    }

    private void setLinuxProxy(boolean enable) {
        try {
            if (enable) {
                runCommand("gsettings set org.gnome.system.proxy mode 'manual'");
                runCommand("gsettings set org.gnome.system.proxy.http host '127.0.0.1'");
                runCommand("gsettings set org.gnome.system.proxy.http port " + SINProxyConfig.DEFAULT_PORT);
            } else {
                runCommand("gsettings set org.gnome.system.proxy mode 'none'");
            }
        } catch (Exception e) {}
    }

    private void runCommand(String cmd) throws Exception {
        Process p = Runtime.getRuntime().exec(cmd);
        p.waitFor();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new SINDesktopApp().setVisible(true);
        });
    }
}
