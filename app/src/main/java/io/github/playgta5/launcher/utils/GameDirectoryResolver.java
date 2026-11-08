package io.github.playgta5.launcher.utils;

import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Intelligent directory resolver that automatically detects the WebGPU web root
 * (where index.html and b/ reside) and the game data root (where common/ and x64/ reside)
 * regardless of whether the user extracted GTA5Webport.zip with a wrapper folder,
 * mirror/playgta5.com, playgta5.com, or directly into root.
 */
public class GameDirectoryResolver {
    private static final String TAG = "GameDirResolver";

    public static class ResolvedPaths {
        public final File baseDir;
        public final File webRootDir;
        public final File dataDir;

        public ResolvedPaths(File baseDir, File webRootDir, File dataDir) {
            this.baseDir = baseDir;
            this.webRootDir = webRootDir;
            this.dataDir = dataDir;
        }

        public boolean hasWebEngine() {
            return webRootDir != null && new File(webRootDir, "index.html").exists();
        }

        public boolean hasGameData() {
            if (dataDir == null || !dataDir.exists()) return false;
            // Check for manifest or common or x64
            return new File(dataDir, "manifest.json").exists()
                    || new File(dataDir, "common").exists()
                    || new File(dataDir, "x64").exists()
                    || new File(dataDir, "bootset.json").exists();
        }

        public boolean isValid() {
            return hasWebEngine() || hasGameData();
        }
    }

    public static ResolvedPaths resolve(File baseDir) {
        if (baseDir == null || !baseDir.exists()) {
            return new ResolvedPaths(baseDir, null, null);
        }

        File resolvedWebRoot = findWebRootDir(baseDir);
        File resolvedDataDir = findDataDir(baseDir, resolvedWebRoot);

        Log.i(TAG, "Resolved for base [" + baseDir.getAbsolutePath() + "]:");
        Log.i(TAG, "  Web Root: " + (resolvedWebRoot != null ? resolvedWebRoot.getAbsolutePath() : "NONE"));
        Log.i(TAG, "  Data Dir: " + (resolvedDataDir != null ? resolvedDataDir.getAbsolutePath() : "NONE"));

        return new ResolvedPaths(baseDir, resolvedWebRoot, resolvedDataDir);
    }

    private static File findWebRootDir(File baseDir) {
        // 1. Direct check in baseDir
        if (new File(baseDir, "index.html").exists()) {
            return baseDir;
        }

        // 2. Common nested paths in GTA5Webport archives
        String[] candidates = new String[] {
                "playgta5.com",
                "mirror/playgta5.com",
                "GTA5Webport",
                "GTA5Webport/playgta5.com",
                "GTA5Webport/mirror/playgta5.com"
        };

        for (String c : candidates) {
            File cand = new File(baseDir, c);
            if (cand.exists() && new File(cand, "index.html").exists()) {
                return cand;
            }
        }

        // 3. Recursive search up to 3 levels deep for index.html
        return searchFile(baseDir, "index.html", 3);
    }

    private static File findDataDir(File baseDir, File webRootDir) {
        // 1. If webRootDir was found, check webRootDir/data
        if (webRootDir != null) {
            File inWeb = new File(webRootDir, "data");
            if (inWeb.exists() && isDataDirectory(inWeb)) {
                return inWeb;
            }
            if (isDataDirectory(webRootDir)) {
                return webRootDir;
            }
        }

        // 2. Direct check in baseDir
        File inBase = new File(baseDir, "data");
        if (inBase.exists() && isDataDirectory(inBase)) {
            return inBase;
        }
        if (isDataDirectory(baseDir)) {
            return baseDir;
        }

        // 3. Common nested candidate paths
        String[] candidates = new String[] {
                "playgta5.com/data",
                "playgta5.com",
                "mirror/playgta5.com/data",
                "mirror/playgta5.com",
                "GTA5Webport/playgta5.com/data",
                "GTA5Webport/data"
        };

        for (String c : candidates) {
            File cand = new File(baseDir, c);
            if (cand.exists() && isDataDirectory(cand)) {
                return cand;
            }
        }

        // 4. Recursive search up to 3 levels deep for common or manifest.json
        File foundManifest = searchFile(baseDir, "manifest.json", 3);
        if (foundManifest != null && isDataDirectory(foundManifest)) {
            return foundManifest;
        }

        File foundCommon = searchDir(baseDir, "common", 3);
        if (foundCommon != null) {
            // If the folder is named "common", the data dir is its parent
            return foundCommon.getParentFile();
        }

        // Fallback: return data subfolder or baseDir
        return inBase.exists() ? inBase : baseDir;
    }

    private static boolean isDataDirectory(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return false;
        return new File(dir, "common").exists()
                || new File(dir, "x64").exists()
                || new File(dir, "manifest.json").exists()
                || new File(dir, "RadioLog.csv").exists();
    }

    private static File searchFile(File dir, String filename, int maxDepth) {
        if (dir == null || !dir.exists() || !dir.isDirectory() || maxDepth < 0) return null;
        if (new File(dir, filename).exists()) return dir;

        File[] list = dir.listFiles();
        if (list == null) return null;

        for (File child : list) {
            if (child.isDirectory()) {
                File res = searchFile(child, filename, maxDepth - 1);
                if (res != null) return res;
            }
        }
        return null;
    }

    private static File searchDir(File dir, String targetDirName, int maxDepth) {
        if (dir == null || !dir.exists() || !dir.isDirectory() || maxDepth < 0) return null;

        File[] list = dir.listFiles();
        if (list == null) return null;

        for (File child : list) {
            if (child.isDirectory()) {
                if (child.getName().equalsIgnoreCase(targetDirName)) {
                    return child;
                }
                File res = searchDir(child, targetDirName, maxDepth - 1);
                if (res != null) return res;
            }
        }
        return null;
    }
}

