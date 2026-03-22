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
 * Manages external assets (tun2socks, wintun) in a persistent app directory.
 */
public class AssetManager {
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    public static File getBinDir() {
        String appData = System.getenv("APPDATA");
        if (appData == null) appData = System.getProperty("user.home");
        File dir = new File(appData, "SINproxy/bin");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static boolean ensureAssets() {
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("win")) return true;

        File binDir = getBinDir();
        boolean success = true;
        
        success &= ensureFile(new File(binDir, "tun2socks.exe"), "https://github.com/xjasonlyu/tun2socks/releases/download/v2.5.2/tun2socks-windows-amd64.exe");
        success &= ensureFile(new File(binDir, "wintun.dll"), "https://github.com/sinproxy/binaries/raw/main/wintun.dll");
        
        return success;
    }

    private static boolean ensureFile(File file, String url) {
        if (file.exists()) return true;

        SINLog.i("Downloading dependency: " + file.getName() + " to " + file.getParent());
        try {
            download(url, file);
            SINLog.i("Successfully downloaded " + file.getName());
            return true;
        } catch (IOException e) {
            SINLog.e("Failed to download " + file.getName(), e);
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
