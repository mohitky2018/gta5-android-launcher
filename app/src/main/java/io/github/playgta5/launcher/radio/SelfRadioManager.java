package io.github.playgta5.launcher.radio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SelfRadioManager {
    private static final String TAG = "SelfRadioManager";
    private static SelfRadioManager instance;

    public static class Song {
        public final String name;
        public final File file;

        public Song(String name, File file) {
            this.name = name;
            this.file = file;
        }
    }

    private final Context context;
    private final List<Song> playlist = new ArrayList<>();
    private MediaPlayer mediaPlayer;
    private int currentIndex = 0;
    private boolean isPlaying = false;

    public static synchronized SelfRadioManager getInstance(Context context) {
        if (instance == null) {
            instance = new SelfRadioManager(context.getApplicationContext());
        }
        return instance;
    }

    private SelfRadioManager(Context context) {
        this.context = context;
        scanRadioFolder();
    }

    public File getRadioDir() {
        File dir = new File(context.getExternalFilesDir(null), "User Music");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public void scanRadioFolder() {
        playlist.clear();
        File dir = getRadioDir();
        File[] files = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".ogg") || lower.endsWith(".wav");
        });

        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                int dot = name.lastIndexOf('.');
                if (dot > 0) name = name.substring(0, dot);
                playlist.add(new Song(name, f));
            }
        }
    }

    public List<Song> getPlaylist() {
        return Collections.unmodifiableList(playlist);
    }

    public void playSong(int index) {
        if (index < 0 || index >= playlist.size()) return;
        currentIndex = index;
        Song song = playlist.get(index);

        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .build());
            mediaPlayer.setDataSource(context, Uri.fromFile(song.file));
            mediaPlayer.setOnCompletionListener(mp -> playNext());
            mediaPlayer.prepare();
            mediaPlayer.start();
            isPlaying = true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to play song: " + song.name, e);
        }
    }

    public void playNext() {
        if (playlist.isEmpty()) return;
        currentIndex = (currentIndex + 1) % playlist.size();
        playSong(currentIndex);
    }

    public void playPrev() {
        if (playlist.isEmpty()) return;
        currentIndex = (currentIndex - 1 + playlist.size()) % playlist.size();
        playSong(currentIndex);
    }

    public void togglePlayPause() {
        if (mediaPlayer == null) {
            if (!playlist.isEmpty()) playSong(0);
            return;
        }
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPlaying = false;
        } else {
            mediaPlayer.start();
            isPlaying = true;
        }
    }

    public boolean isPlaying() {
        return isPlaying && mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public String getCurrentSongName() {
        if (currentIndex >= 0 && currentIndex < playlist.size()) {
            return playlist.get(currentIndex).name;
        }
        return "No Track Loaded";
    }

    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        isPlaying = false;
    }
}

