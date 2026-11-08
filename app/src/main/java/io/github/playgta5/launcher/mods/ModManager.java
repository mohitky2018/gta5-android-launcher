package io.github.playgta5.launcher.mods;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ModManager {

    public static class ModItem {
        public final String name;
        public final File file;
        public boolean isEnabled;

        public ModItem(String name, File file, boolean isEnabled) {
            this.name = name;
            this.file = file;
            this.isEnabled = isEnabled;
        }
    }

    private final Context context;
    private final List<ModItem> modsList = new ArrayList<>();

    public ModManager(Context context) {
        this.context = context;
        scanMods();
    }

    public File getScriptsDir() {
        File dir = new File(context.getExternalFilesDir(null), "scripts");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public void scanMods() {
        modsList.clear();
        File dir = getScriptsDir();
        File[] files = dir.listFiles();

        if (files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    String name = f.getName();
                    boolean disabled = name.endsWith(".disabled");
                    String cleanName = disabled ? name.substring(0, name.length() - ".disabled".length()) : name;

                    if (cleanName.endsWith(".asi") || cleanName.endsWith(".dll") || cleanName.endsWith(".lua") || cleanName.endsWith(".cs")) {
                        modsList.add(new ModItem(cleanName, f, !disabled));
                    }
                }
            }
        }
    }

    public List<ModItem> getModsList() {
        return Collections.unmodifiableList(modsList);
    }

    public boolean toggleMod(ModItem mod) {
        File current = mod.file;
        File target;
        if (mod.isEnabled) {
            target = new File(current.getParentFile(), mod.name + ".disabled");
        } else {
            target = new File(current.getParentFile(), mod.name);
        }

        if (current.renameTo(target)) {
            scanMods();
            return true;
        }
        return false;
    }
}

