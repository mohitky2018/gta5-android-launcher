package io.github.playgta5.launcher.cheats;

import android.webkit.WebView;

public class CheatsManager {
    private static CheatsManager instance;

    private boolean godMode = false;
    private boolean neverWanted = false;

    public static synchronized CheatsManager getInstance() {
        if (instance == null) {
            instance = new CheatsManager();
        }
        return instance;
    }

    public boolean isGodMode() {
        return godMode;
    }

    public void setGodMode(WebView webView, boolean enabled) {
        this.godMode = enabled;
        executeJs(webView, String.format("if (window.PGCheat) PGCheat.setGodMode(%b);", enabled));
    }

    public boolean isNeverWanted() {
        return neverWanted;
    }

    public void setNeverWanted(WebView webView, boolean enabled) {
        this.neverWanted = enabled;
        executeJs(webView, String.format("if (window.PGCheat) PGCheat.setNeverWanted(%b);", enabled));
    }

    public void clearWantedLevel(WebView webView) {
        executeJs(webView, "if (window.PGCheat) PGCheat.clearWanted();");
    }

    public void maxHealthAndArmor(WebView webView) {
        executeJs(webView, "if (window.PGCheat) PGCheat.maxHealthAndArmor();");
    }

    public void giveAllWeapons(WebView webView) {
        executeJs(webView, "if (window.PGCheat) PGCheat.giveAllWeapons();");
    }

    public void repairVehicle(WebView webView) {
        executeJs(webView, "if (window.PGCheat) PGCheat.repairCurrentVehicle();");
    }

    public void setWeather(WebView webView, String weather) {
        executeJs(webView, String.format("if (window.PGCheat) PGCheat.setWeather('%s');", weather));
    }

    public void setTime(WebView webView, int hours, int minutes) {
        executeJs(webView, String.format("if (window.PGCheat) PGCheat.setTime(%d, %d);", hours, minutes));
    }

    public void addMoney(WebView webView, int amount) {
        executeJs(webView, String.format("if (window.PGCheat) PGCheat.addMoney(%d);", amount));
    }

    public void spawnVehicle(WebView webView, String modelName, boolean warpIn) {
        executeJs(webView, String.format("if (window.PGCheat) PGCheat.spawnVehicle('%s', %b);", modelName, warpIn));
    }

    private void executeJs(WebView webView, String js) {
        if (webView != null) {
            webView.post(() -> webView.evaluateJavascript(js, null));
        }
    }
}

