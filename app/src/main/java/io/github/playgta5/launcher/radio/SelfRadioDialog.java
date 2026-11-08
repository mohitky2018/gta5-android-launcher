package io.github.playgta5.launcher.radio;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.List;

import io.github.playgta5.launcher.R;

public class SelfRadioDialog extends Dialog {

    private final SelfRadioManager radioManager;
    private TextView textCurrentSong;
    private ImageButton btnPlayPause;
    private ListView listTracks;
    private TrackAdapter adapter;

    public SelfRadioDialog(@NonNull Context context) {
        super(context, R.style.Theme_GTA5Launcher);
        this.radioManager = SelfRadioManager.getInstance(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_self_radio);

        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        TextView textFolderPath = findViewById(R.id.text_radio_folder_path);
        textFolderPath.setText("Folder: " + radioManager.getRadioDir().getAbsolutePath());

        textCurrentSong = findViewById(R.id.text_current_song);
        btnPlayPause = findViewById(R.id.btn_radio_play_pause);
        ImageButton btnPrev = findViewById(R.id.btn_radio_prev);
        ImageButton btnNext = findViewById(R.id.btn_radio_next);
        Button btnRescan = findViewById(R.id.btn_rescan_radio);
        listTracks = findViewById(R.id.list_radio_tracks);

        adapter = new TrackAdapter();
        listTracks.setAdapter(adapter);

        updatePlaybackUI();

        btnPlayPause.setOnClickListener(v -> {
            radioManager.togglePlayPause();
            updatePlaybackUI();
        });

        btnPrev.setOnClickListener(v -> {
            radioManager.playPrev();
            updatePlaybackUI();
        });

        btnNext.setOnClickListener(v -> {
            radioManager.playNext();
            updatePlaybackUI();
        });

        btnRescan.setOnClickListener(v -> {
            radioManager.scanRadioFolder();
            adapter.notifyDataSetChanged();
            updatePlaybackUI();
            Toast.makeText(getContext(), "Scanned " + radioManager.getPlaylist().size() + " audio tracks", Toast.LENGTH_SHORT).show();
        });
    }

    private void updatePlaybackUI() {
        if (textCurrentSong != null) {
            textCurrentSong.setText(radioManager.getCurrentSongName());
        }
    }

    private class TrackAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return radioManager.getPlaylist().size();
        }

        @Override
        public Object getItem(int position) {
            return radioManager.getPlaylist().get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_vehicle, parent, false);
            }

            SelfRadioManager.Song song = radioManager.getPlaylist().get(position);

            TextView textTitle = convertView.findViewById(R.id.text_vehicle_name);
            TextView textDesc = convertView.findViewById(R.id.text_vehicle_model);
            Button btnPlay = convertView.findViewById(R.id.btn_spawn);

            textTitle.setText(song.name);
            long kb = song.file.length() / 1024;
            textDesc.setText(String.format("%.1f MB • %s", kb / 1024f, song.file.getName()));
            btnPlay.setText("PLAY");

            btnPlay.setOnClickListener(v -> {
                radioManager.playSong(position);
                updatePlaybackUI();
            });

            return convertView;
        }
    }
}

