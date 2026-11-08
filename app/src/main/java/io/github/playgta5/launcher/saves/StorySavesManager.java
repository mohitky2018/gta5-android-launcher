package io.github.playgta5.launcher.saves;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StorySavesManager {
    private static final String TAG = "StorySavesManager";

    public static class SaveSlot {
        public final String slotName;
        public final File file;
        public final long lastModified;
        public final long fileSize;

        public SaveSlot(String slotName, File file, long lastModified, long fileSize) {
            this.slotName = slotName;
            this.file = file;
            this.lastModified = lastModified;
            this.fileSize = fileSize;
        }

        public String getFormattedDate() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(lastModified));
        }
    }

    private final Context context;

    public StorySavesManager(Context context) {
        this.context = context;
    }

    public File getSavesDirectory() {
        File dir = new File(context.getExternalFilesDir(null), "Saves");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public List<SaveSlot> listSaves() {
        List<SaveSlot> list = new ArrayList<>();
        File dir = getSavesDirectory();
        File[] files = dir.listFiles((d, name) -> name.startsWith("SGTA5") || name.endsWith(".save"));

        if (files != null) {
            for (File f : files) {
                list.add(new SaveSlot(f.getName(), f, f.lastModified(), f.length()));
            }
        }
        Collections.sort(list, (a, b) -> Long.compare(b.lastModified, a.lastModified));
        return list;
    }

    public boolean importSaveFile(File sourceFile, String targetSlotName) {
        File targetFile = new File(getSavesDirectory(), targetSlotName);
        try (FileInputStream in = new FileInputStream(sourceFile);
             FileOutputStream out = new FileOutputStream(targetFile)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error importing save file", e);
            return false;
        }
    }

    public boolean deleteSave(SaveSlot slot) {
        if (slot != null && slot.file != null && slot.file.exists()) {
            return slot.file.delete();
        }
        return false;
    }
}

