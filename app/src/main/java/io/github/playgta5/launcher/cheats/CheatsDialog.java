package io.github.playgta5.launcher.cheats;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;

import io.github.playgta5.launcher.R;

public class CheatsDialog extends Dialog {

    private final WebView webView;
    private final CheatsManager cheatsManager = CheatsManager.getInstance();

    public CheatsDialog(@NonNull Context context, WebView webView) {
        super(context, R.style.Theme_GTA5Launcher);
        this.webView = webView;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_cheats);

        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        SwitchCompat switchGod = findViewById(R.id.switch_god_mode);
        if (switchGod != null) {
            switchGod.setChecked(cheatsManager.isGodMode());
            switchGod.setOnCheckedChangeListener((btn, isChecked) -> cheatsManager.setGodMode(webView, isChecked));
        }

        SwitchCompat switchWanted = findViewById(R.id.switch_never_wanted);
        if (switchWanted != null) {
            switchWanted.setChecked(cheatsManager.isNeverWanted());
            switchWanted.setOnCheckedChangeListener((btn, isChecked) -> cheatsManager.setNeverWanted(webView, isChecked));
        }

        Button btnClearWanted = findViewById(R.id.btn_clear_wanted);
        if (btnClearWanted != null) {
            btnClearWanted.setOnClickListener(v -> cheatsManager.clearWantedLevel(webView));
        }

        Button btnMaxHealth = findViewById(R.id.btn_max_health);
        if (btnMaxHealth != null) {
            btnMaxHealth.setOnClickListener(v -> cheatsManager.maxHealthAndArmor(webView));
        }

        Button btnGiveWeapons = findViewById(R.id.btn_give_weapons);
        if (btnGiveWeapons != null) {
            btnGiveWeapons.setOnClickListener(v -> cheatsManager.giveAllWeapons(webView));
        }

        Button btnRepairVehicle = findViewById(R.id.btn_repair_vehicle);
        if (btnRepairVehicle != null) {
            btnRepairVehicle.setOnClickListener(v -> cheatsManager.repairVehicle(webView));
        }

        // Weather
        findViewById(R.id.btn_weather_sunny).setOnClickListener(v -> cheatsManager.setWeather(webView, "EXTRASUNNY"));
        findViewById(R.id.btn_weather_clear).setOnClickListener(v -> cheatsManager.setWeather(webView, "CLEAR"));
        findViewById(R.id.btn_weather_thunder).setOnClickListener(v -> cheatsManager.setWeather(webView, "THUNDER"));

        // Time
        findViewById(R.id.btn_time_morning).setOnClickListener(v -> cheatsManager.setTime(webView, 8, 0));
        findViewById(R.id.btn_time_noon).setOnClickListener(v -> cheatsManager.setTime(webView, 12, 0));
        findViewById(R.id.btn_time_night).setOnClickListener(v -> cheatsManager.setTime(webView, 0, 0));

        // Cash
        findViewById(R.id.btn_money_100k).setOnClickListener(v -> cheatsManager.addMoney(webView, 100000));
        findViewById(R.id.btn_money_1m).setOnClickListener(v -> cheatsManager.addMoney(webView, 1000000));

        // Open Spawner
        Button btnOpenSpawner = findViewById(R.id.btn_open_spawner);
        if (btnOpenSpawner != null) {
            btnOpenSpawner.setOnClickListener(v -> {
                dismiss();
                new VehicleSpawnerDialog(getContext(), webView).show();
            });
        }
    }
}

