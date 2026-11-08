package io.github.playgta5.launcher;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.hardware.input.InputManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.InputDevice;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.List;

import io.github.playgta5.launcher.cheats.CheatsDialog;
import io.github.playgta5.launcher.installer.GameInstaller;
import io.github.playgta5.launcher.settings.GameSettings;
import io.github.playgta5.launcher.settings.SettingsDialog;
import io.github.playgta5.launcher.utils.CreditsDialog;
import io.github.playgta5.launcher.utils.ErrorDialog;
import io.github.playgta5.launcher.utils.GameDirectoryResolver;
import io.github.playgta5.launcher.utils.PathUtils;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "GTA5MainActivity";

    private GameSettings settings;
    private GameInstaller installer;

    private TextView textFilesBadge;
    private TextView textFilesSummary;
    private ProgressBar progressFiles;
    private TextView textGraphicsSummary;
    private TextView textGamepadStatus;
    private RadioGroup groupGameMode;

    // Folder Picker
    private final ActivityResultLauncher<Intent> folderPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri treeUri = result.getData().getData();
                    if (treeUri != null) {
                        handleSelectedFolderUri(treeUri, result.getData().getFlags());
                    }
                }
            });

    // Android 11+ All Files Access Permission Launcher
    private final ActivityResultLauncher<Intent> allFilesPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (checkStoragePermission()) {
                    Toast.makeText(this, "Storage access granted!", Toast.LENGTH_SHORT).show();
                    runFileVerification();
                } else {
                    Toast.makeText(this, "All Files Access is needed to read game files", Toast.LENGTH_LONG).show();
                }
            });

    // Android 10 and below Storage Permission Launcher
    private final ActivityResultLauncher<String[]> legacyPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean granted = true;
                for (Boolean b : result.values()) {
                    if (b != null && !b) {
                        granted = false;
                        break;
                    }
                }
                if (granted) {
                    runFileVerification();
                } else {
                    Toast.makeText(this, "Storage permission required to read game files", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Global crash handler to display clean error dialog instead of crashing
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e(TAG, "Uncaught exception in MainActivity", throwable);
            new Handler(Looper.getMainLooper()).post(() -> {
                ErrorDialog.show(MainActivity.this, "Application Exception",
                        "An unexpected error occurred in the launcher:\n" + throwable.getMessage(), throwable);
            });
        });

        try {
            setContentView(R.layout.activity_main);

            settings = new GameSettings(this);
            installer = new GameInstaller(this);

            initViews();
            applyCustomFonts();
            checkControllers();
            checkAndRequestStoragePermissions(false);
            runFileVerification();

        } catch (Throwable t) {
            Log.e(TAG, "Error initializing MainActivity", t);
            ErrorDialog.show(this, "Launcher Initialization Error",
                    "Failed to start launcher interface:\n" + t.getMessage(), t);
        }
    }

    private void initViews() {
        textFilesBadge = findViewById(R.id.text_files_badge);
        textFilesSummary = findViewById(R.id.text_files_summary);
        progressFiles = findViewById(R.id.progress_files);
        textGraphicsSummary = findViewById(R.id.text_graphics_summary);
        textGamepadStatus = findViewById(R.id.text_gamepad_status);
        groupGameMode = findViewById(R.id.group_game_mode);

        // Game Mode Radio
        String currentMode = settings.getGameMode();
        if (GameSettings.MODE_STORY.equals(currentMode)) {
            ((RadioButton) findViewById(R.id.radio_mode_story)).setChecked(true);
        } else if (GameSettings.MODE_ENV_TEST.equals(currentMode)) {
            ((RadioButton) findViewById(R.id.radio_mode_env_test)).setChecked(true);
        } else {
            ((RadioButton) findViewById(R.id.radio_mode_sandbox)).setChecked(true);
        }

        groupGameMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_mode_story) {
                settings.setGameMode(GameSettings.MODE_STORY);
            } else if (checkedId == R.id.radio_mode_env_test) {
                settings.setGameMode(GameSettings.MODE_ENV_TEST);
            } else {
                settings.setGameMode(GameSettings.MODE_SANDBOX);
            }
        });

        // Launch Game Button
        Button btnLaunch = findViewById(R.id.btn_launch_game);
        btnLaunch.setOnClickListener(v -> safeLaunchGame());

        // Folder Picker Button
        Button btnChooseFolder = findViewById(R.id.btn_choose_folder);
        btnChooseFolder.setOnClickListener(v -> {
            if (checkAndRequestStoragePermissions(true)) {
                openFolderPicker();
            }
        });

        // Enter Path Manually Button
        Button btnManualPath = findViewById(R.id.btn_manual_path);
        btnManualPath.setOnClickListener(v -> showManualPathDialog());

        // Verify Manifest Button
        Button btnVerify = findViewById(R.id.btn_verify_manifest);
        btnVerify.setOnClickListener(v -> runFileVerification());

        // Settings Dialog Button
        Button btnEditSettings = findViewById(R.id.btn_edit_settings);
        btnEditSettings.setOnClickListener(v -> {
            new SettingsDialog(MainActivity.this, this::updateGraphicsSummary).show();
        });

        // Cheats Dialog Button
        Button btnCheats = findViewById(R.id.btn_main_cheats);
        btnCheats.setOnClickListener(v -> {
            new CheatsDialog(MainActivity.this, null).show();
        });

        // Credits Dialog Button & Footer
        Button btnCredits = findViewById(R.id.btn_main_credits);
        if (btnCredits != null) {
            btnCredits.setOnClickListener(v -> CreditsDialog.show(MainActivity.this));
        }

        View footerCredits = findViewById(R.id.text_footer_credits);
        if (footerCredits != null) {
            footerCredits.setOnClickListener(v -> CreditsDialog.show(MainActivity.this));
        }

        updateGraphicsSummary();
    }

    private void openFolderPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            folderPickerLauncher.launch(intent);
        } catch (Throwable t) {
            Log.e(TAG, "Cannot launch folder picker", t);
            Toast.makeText(this, "File picker error, use 'Enter Path' instead", Toast.LENGTH_SHORT).show();
            showManualPathDialog();
        }
    }

    private void handleSelectedFolderUri(Uri treeUri, int flags) {
        try {
            // Persist URI permissions
            final int takeFlags = (flags != 0 ? flags : (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
        } catch (Throwable t) {
            Log.w(TAG, "Could not persist URI permission", t);
        }

        File resolved = PathUtils.getFileFromUri(this, treeUri);
        String finalPath;
        if (resolved != null) {
            finalPath = resolved.getAbsolutePath();
        } else {
            // Fallback to storing tree uri string
            finalPath = treeUri.toString();
        }

        settings.setGameDirectoryPath(finalPath);

        // Immediate visual confirmation
        if (textFilesBadge != null) {
            textFilesBadge.setText("Selected");
            textFilesBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_green));
        }
        if (textFilesSummary != null) {
            textFilesSummary.setText("Folder: " + finalPath);
        }

        Toast.makeText(this, "Folder Selected:\n" + finalPath, Toast.LENGTH_LONG).show();
        runFileVerification();
    }

    private void showManualPathDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_GTA5Launcher);
        builder.setTitle("Enter Game Folder Path");

        final EditText input = new EditText(this);
        input.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        input.setHintTextColor(ContextCompat.getColor(this, R.color.text_muted));
        input.setHint("/storage/emulated/0/Download/GTA5");

        String current = settings.getGameDirectoryPath();
        if (current != null && !current.isEmpty()) {
            input.setText(current);
        } else {
            input.setText(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download/GTA5");
        }
        input.setSelection(input.getText().length());

        builder.setView(input);
        builder.setPositiveButton("Set Folder", (dialog, which) -> {
            String path = input.getText().toString().trim();
            if (!path.isEmpty()) {
                settings.setGameDirectoryPath(path);
                if (textFilesBadge != null) {
                    textFilesBadge.setText("Set");
                    textFilesBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_green));
                }
                if (textFilesSummary != null) {
                    textFilesSummary.setText("Folder: " + path);
                }
                runFileVerification();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private boolean checkAndRequestStoragePermissions(boolean userTriggered) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                if (userTriggered) {
                    new AlertDialog.Builder(this, R.style.Theme_GTA5Launcher)
                            .setTitle("Storage Permission Required")
                            .setMessage("To access the GTA V game files in your device storage, Android requires All Files Access permission.")
                            .setPositiveButton("Open Settings", (d, w) -> {
                                try {
                                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                                    intent.setData(Uri.parse("package:" + getPackageName()));
                                    allFilesPermissionLauncher.launch(intent);
                                } catch (Throwable t) {
                                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                                    allFilesPermissionLauncher.launch(intent);
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                }
                return false;
            }
            return true;
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                if (userTriggered) {
                    legacyPermissionsLauncher.launch(new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    });
                }
                return false;
            }
            return true;
        }
    }

    private void safeLaunchGame() {
        try {
            File dir = resolveGameDir();
            boolean hasFiles = dir != null && dir.exists() && dir.listFiles() != null && dir.listFiles().length > 0;

            if (!hasFiles) {
                new AlertDialog.Builder(this, R.style.Theme_GTA5Launcher)
                        .setTitle("Start Game Engine")
                        .setMessage("No game assets were detected in the selected folder.\n\nThe launcher will start the embedded WebGPU engine host and shader pipeline in diagnostic mode.\n\nDo you want to proceed?")
                        .setPositiveButton("Start Engine", (d, w) -> launchGameActivity())
                        .setNegativeButton("Select Folder First", (d, w) -> openFolderPicker())
                        .show();
            } else {
                launchGameActivity();
            }

        } catch (Throwable t) {
            Log.e(TAG, "Error checking launch requirements", t);
            ErrorDialog.show(this, "Launch Check Failed",
                    "Could not verify game requirements: " + t.getMessage(), t);
        }
    }

    private void launchGameActivity() {
        try {
            Intent intent = new Intent(MainActivity.this, GameActivity.class);
            startActivity(intent);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to start GameActivity", t);
            ErrorDialog.show(this, "Game Launch Error",
                    "GameActivity could not be launched:\n" + t.getMessage(), t);
        }
    }

    private File resolveGameDir() {
        String path = settings.getGameDirectoryPath();
        if (path != null && !path.isEmpty()) {
            File direct = new File(path);
            if (direct.exists()) return direct;

            try {
                File fromUri = PathUtils.getFileFromUri(this, Uri.parse(path));
                if (fromUri != null && fromUri.exists()) return fromUri;
            } catch (Throwable ignored) {}
        }

        // Check fallback app data directory
        File appData = getExternalFilesDir(null);
        if (appData != null && appData.exists()) {
            File mirror = new File(appData, "data");
            if (mirror.exists()) return mirror;
        }

        return null;
    }

    private void applyCustomFonts() {
        try {
            Typeface chaletLondon = Typeface.createFromAsset(getAssets(), "fonts/ChaletLondonNineteenSixty.ttf");
            TextView title = findViewById(R.id.text_app_title);
            if (title != null && chaletLondon != null) {
                title.setTypeface(chaletLondon);
            }
        } catch (Throwable ignored) {}
    }

    private void updateGraphicsSummary() {
        try {
            textGraphicsSummary.setText(settings.describe());
        } catch (Throwable ignored) {}
    }

    private void checkControllers() {
        try {
            InputManager im = (InputManager) getSystemService(Context.INPUT_SERVICE);
            if (im != null) {
                int[] ids = im.getInputDeviceIds();
                for (int id : ids) {
                    InputDevice dev = im.getInputDevice(id);
                    if (dev != null && (dev.getSources() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) {
                        textGamepadStatus.setText(getString(R.string.gamepad_connected, dev.getName()));
                        return;
                    }
                }
            }
            textGamepadStatus.setText(getString(R.string.gamepad_not_connected));
        } catch (Throwable ignored) {}
    }

    private void runFileVerification() {
        File dir = resolveGameDir();

        if (dir == null || !dir.exists()) {
            String currentPath = settings.getGameDirectoryPath();
            if (currentPath != null && !currentPath.isEmpty()) {
                textFilesBadge.setText("Path Set");
                textFilesBadge.setTextColor(ContextCompat.getColor(this, R.color.gta_gold));
                textFilesSummary.setText("Configured path: " + currentPath + "\n(Folder not found or permission needed)");
            } else {
                textFilesBadge.setText("No Folder");
                textFilesBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_red));
                textFilesSummary.setText("No game folder selected. Tap 'Choose Folder' or 'Enter Path'.");
            }
            return;
        }

        GameDirectoryResolver.ResolvedPaths res = GameDirectoryResolver.resolve(dir);
        File effectiveDir = res.dataDir != null && res.dataDir.exists() ? res.dataDir : dir;

        progressFiles.setVisibility(View.VISIBLE);
        progressFiles.setIndeterminate(true);
        textFilesBadge.setText("Verifying...");
        textFilesBadge.setTextColor(ContextCompat.getColor(this, R.color.gta_gold));
        textFilesSummary.setText("Scanning: " + dir.getAbsolutePath() + (res.hasWebEngine() ? "\n(Engine + Assets found)" : ""));

        installer.verifyFiles(dir, new GameInstaller.InstallCallback() {
            @Override
            public void onProgress(int current, int total, long currentBytes, long totalBytes, String currentFile) {
                progressFiles.setIndeterminate(false);
                progressFiles.setMax(total);
                progressFiles.setProgress(current);
                textFilesSummary.setText(String.format("Checking (%d/%d): %s", current, total, currentFile));
            }

            @Override
            public void onComplete(boolean success, int verified, int missing, List<String> missingFiles) {
                progressFiles.setVisibility(View.GONE);
                if (success) {
                    textFilesBadge.setText("Verified");
                    textFilesBadge.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.accent_green));
                    textFilesSummary.setText(String.format("All %d files verified at:\n%s", verified, effectiveDir.getAbsolutePath()));
                } else {
                    textFilesBadge.setText(missing + " Missing");
                    textFilesBadge.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.gta_gold));
                    textFilesSummary.setText(String.format("Folder: %s\n%d files verified, %d files missing/needed.", effectiveDir.getAbsolutePath(), verified, missing));
                }
            }

            @Override
            public void onError(String message) {
                progressFiles.setVisibility(View.GONE);
                textFilesBadge.setText("Check Error");
                textFilesBadge.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.accent_red));
                textFilesSummary.setText("Verification notice: " + message);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkControllers();
    }
}
