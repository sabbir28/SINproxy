package com.sinproxy.android.utils;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Manages external assets (tun2socks, wintun) for SINproxy Desktop.
 */
public class AssetManager {
    private static final String TUN2SOCKS_URL = "https://github.com/xjasonlyu/tun2socks/releases/download/v2.5.2/tun2socks-windows-amd64.exe"; // Assume renamed for ease
    private static final String WINTUN_URL = "https://github.com/sinproxy/binaries/raw/main/wintun.dll"; // Placeholder for verified binary

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    public static boolean ensureAssets() {
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("win")) return true;

        boolean success = true;
        success &= ensureFile("tun2socks.exe", "https://github.com/xjasonlyu/tun2socks/releases/download/v2.5.2/tun2socks-windows-amd64.exe");
        success &= ensureFile("wintun.dll", "https://github.com/sinproxy/binaries/raw/main/wintun.dll");
        
        return success;
    }

    private static boolean ensureFile(String filename, String url) {
        File file = new File(filename);
        if (file.exists()) return true;

        SINLog.i("Downloading dependency: " + filename + "...");
        try {
            download(url, file);
            SINLog.i("Successfully downloaded " + filename);
            return true;
        } catch (IOException e) {
            SINLog.e("Failed to download " + filename + " from " + url, e);
            return false;
        }
    }

    private static void download(String url, File dest) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            
            try (BufferedSink sink = Okio.buffer(Okio.sink(dest))) {
                sink.writeAll(response.body().source());
            }
        }
    }
}
