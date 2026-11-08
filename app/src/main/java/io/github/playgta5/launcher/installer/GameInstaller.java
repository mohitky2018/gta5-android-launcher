package io.github.playgta5.launcher.installer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class GameInstaller {
    private static final String TAG = "GTA5GameInstaller";

    public interface InstallCallback {
        void onProgress(int current, int total, long currentBytes, long totalBytes, String currentFile);
        void onComplete(boolean success, int verified, int missing, List<String> missingFiles);
        void onError(String message);
    }

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public GameInstaller(Context context) {
        this.context = context;
    }

    public void verifyFiles(File gameDir, InstallCallback callback) {
        executor.execute(() -> {
            try {
                // Resolve true data directory (handling mirror/playgta5.com/data, playgta5.com/data, data, etc.)
                io.github.playgta5.launcher.utils.GameDirectoryResolver.ResolvedPaths res =
                        io.github.playgta5.launcher.utils.GameDirectoryResolver.resolve(gameDir);
                File targetDataDir = res.dataDir != null && res.dataDir.exists() ? res.dataDir : gameDir;

                Log.i(TAG, "Starting manifest verification in: " + targetDataDir.getAbsolutePath());

                // Read manifest.json from on-disk if present, otherwise from APK assets
                String jsonStr = null;
                File onDiskManifest = new File(targetDataDir, "manifest.json");
                if (onDiskManifest.exists() && onDiskManifest.isFile()) {
                    try (FileInputStream fis = new FileInputStream(onDiskManifest)) {
                        int size = fis.available();
                        byte[] buffer = new byte[size];
                        fis.read(buffer);
                        jsonStr = new String(buffer, StandardCharsets.UTF_8);
                    } catch (Throwable ignored) {}
                }

                if (jsonStr == null || jsonStr.isEmpty()) {
                    try (InputStream is = context.getAssets().open("manifest.json")) {
                        int size = is.available();
                        byte[] buffer = new byte[size];
                        is.read(buffer);
                        jsonStr = new String(buffer, StandardCharsets.UTF_8);
                    }
                }

                JSONObject manifest = new JSONObject(jsonStr);
                JSONArray filesArray = manifest.getJSONArray("files");
                int totalFiles = filesArray.length();

                long totalBytes = 0;
                long verifiedBytes = 0;
                int verifiedCount = 0;
                int missingCount = 0;
                List<String> missingFiles = new ArrayList<>();

                for (int i = 0; i < totalFiles; i++) {
                    JSONArray item = filesArray.getJSONArray(i);
                    String relPath = item.getString(0);
                    long expectedSize = item.getLong(1);
                    totalBytes += expectedSize;

                    File f = new File(targetDataDir, relPath);
                    if (!f.exists()) {
                        File alt = new File(targetDataDir, "data/" + relPath);
                        if (alt.exists()) f = alt;
                    }
                    if (!f.exists() && res.webRootDir != null) {
                        File alt = new File(res.webRootDir, "data/" + relPath);
                        if (alt.exists()) f = alt;
                    }
                    if (!f.exists()) {
                        File alt = new File(gameDir, relPath);
                        if (alt.exists()) f = alt;
                    }

                    if (f.exists() && f.isFile()) {
                        long actualSize = f.length();
                        if (actualSize == expectedSize || expectedSize == 0) {
                            verifiedCount++;
                            verifiedBytes += actualSize;
                        } else {
                            missingCount++;
                            missingFiles.add(relPath + " (size mismatch: " + actualSize + " vs " + expectedSize + ")");
                        }
                    } else {
                        missingCount++;
                        if (missingFiles.size() < 100) {
                            missingFiles.add(relPath);
                        }
                    }

                    if (i % 50 == 0 || i == totalFiles - 1) {
                        int current = i + 1;
                        long curBytes = verifiedBytes;
                        long totBytes = totalBytes;
                        mainHandler.post(() -> callback.onProgress(current, totalFiles, curBytes, totBytes, relPath));
                    }
                }

                boolean success = (missingCount == 0);
                final int finalVerified = verifiedCount;
                final int finalMissing = missingCount;
                mainHandler.post(() -> callback.onComplete(success, finalVerified, finalMissing, missingFiles));

            } catch (Exception e) {
                Log.e(TAG, "Manifest verification error", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    public void extractArchive(File archiveFile, File targetDir, InstallCallback callback) {
        executor.execute(() -> {
            try {
                if (!targetDir.exists()) targetDir.mkdirs();

                try (ZipInputStream zis = new ZipInputStream(new FileInputStream(archiveFile))) {
                    ZipEntry entry;
                    byte[] buffer = new byte[64 * 1024];
                    int count = 0;

                    while ((entry = zis.getNextEntry()) != null) {
                        File destFile = new File(targetDir, entry.getName());
                        if (entry.isDirectory()) {
                            destFile.mkdirs();
                        } else {
                            File parent = destFile.getParentFile();
                            if (parent != null && !parent.exists()) parent.mkdirs();

                            try (FileOutputStream fos = new FileOutputStream(destFile)) {
                                int len;
                                while ((len = zis.read(buffer)) > 0) {
                                    fos.write(buffer, 0, len);
                                }
                            }
                        }
                        count++;
                        int cur = count;
                        String name = entry.getName();
                        if (count % 20 == 0) {
                            mainHandler.post(() -> callback.onProgress(cur, 0, 0, 0, name));
                        }
                        zis.closeEntry();
                    }
                }

                mainHandler.post(() -> callback.onComplete(true, 0, 0, new ArrayList<>()));

            } catch (Exception e) {
                Log.e(TAG, "Archive extract error", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }
}
