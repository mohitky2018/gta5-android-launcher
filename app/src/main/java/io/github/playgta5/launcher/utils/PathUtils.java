package io.github.playgta5.launcher.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;

import java.io.File;
import java.net.URLDecoder;

public class PathUtils {
    private static final String TAG = "GTA5PathUtils";

    /**
     * Resolves a Storage Access Framework Uri (e.g. from ACTION_OPEN_DOCUMENT_TREE)
     * to a direct physical java.io.File path on disk.
     */
    public static File getFileFromUri(Context context, Uri uri) {
        if (uri == null) return null;

        try {
            // Case 1: Plain file:// URI
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                String path = uri.getPath();
                if (path != null) {
                    File f = new File(path);
                    if (f.exists()) return f;
                }
            }

            // Case 2: DocumentsContract Tree URI (from ACTION_OPEN_DOCUMENT_TREE)
            if (DocumentsContract.isTreeUri(uri)) {
                String treeDocId = DocumentsContract.getTreeDocumentId(uri);
                if (treeDocId != null) {
                    File resolved = resolveDocIdToFile(treeDocId);
                    if (resolved != null) return resolved;
                }
            }

            // Case 3: Document URI (not tree)
            if (DocumentsContract.isDocumentUri(context, uri)) {
                String docId = DocumentsContract.getDocumentId(uri);
                if (docId != null) {
                    File resolved = resolveDocIdToFile(docId);
                    if (resolved != null) return resolved;
                }
            }

            // Case 4: Uri path string parsing fallback
            String rawPath = uri.getPath();
            if (rawPath != null) {
                // Decode URL encoding (e.g. %20 -> space, %3A -> :)
                String decoded = URLDecoder.decode(rawPath, "UTF-8");

                // Check for tree/primary:... or document/primary:...
                int primaryIdx = decoded.indexOf("primary:");
                if (primaryIdx != -1) {
                    String sub = decoded.substring(primaryIdx + "primary:".length());
                    File f = new File(Environment.getExternalStorageDirectory(), sub);
                    if (f.exists()) return f;
                    return f; // Return candidate
                }

                // Check for raw:...
                int rawIdx = decoded.indexOf("raw:");
                if (rawIdx != -1) {
                    String sub = decoded.substring(rawIdx + "raw:".length());
                    File f = new File(sub);
                    if (f.exists()) return f;
                }

                // Check direct string path
                File direct = new File(rawPath);
                if (direct.exists()) return direct;
            }

        } catch (Exception e) {
            Log.w(TAG, "Error resolving URI to File: " + uri, e);
        }

        return null;
    }

    private static File resolveDocIdToFile(String docId) {
        if (docId == null) return null;

        try {
            // Format: "primary:Download/GTA5"
            if (docId.startsWith("primary:")) {
                String relPath = docId.substring("primary:".length());
                return new File(Environment.getExternalStorageDirectory(), relPath);
            }

            // Format: "raw:/storage/emulated/0/Download/GTA5"
            if (docId.startsWith("raw:")) {
                return new File(docId.substring("raw:".length()));
            }

            // Format: "XXXX-XXXX:Folder" (SD Card)
            if (docId.contains(":")) {
                String[] parts = docId.split(":", 2);
                String volume = parts[0];
                String relPath = parts[1];

                File sdCard = new File("/storage/" + volume, relPath);
                if (sdCard.exists()) return sdCard;

                // Fallback to internal if volume matches primary
                return new File(Environment.getExternalStorageDirectory(), relPath);
            }

            // Direct path
            File direct = new File(docId);
            if (direct.exists()) return direct;

        } catch (Exception e) {
            Log.w(TAG, "Failed to resolve docId: " + docId, e);
        }

        return null;
    }
}

