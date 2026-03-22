package com.sinproxy.android;

/**
 * Professional Proxy Configuration for SINproxy Desktop
 */
public class SINProxyConfig {
    // Default Proxy Settings
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 8081;
    public static final int SOCKS_PORT = 1080;
    public static final int BUFFER_SIZE = 16384;
    public static final int PROXY_TIMEOUT = 30000; // 30 seconds

    // SNI Spoofing (Bug Host)
    public static String BUG_SNI = "m.facebook.com";

    // Certificate Settings
    public static final String CA_COMMON_NAME = "SINproxy Root CA";
    public static final String CERT_ALGORITHM = "RSA";
    public static final int KEY_SIZE = 2048;
    
    // File Paths (for internal storage/cache)
    public static String CA_CERT_FILENAME = "sinproxy_ca.crt";
    public static String CA_KEY_FILENAME = "sinproxy_ca.key";
}
