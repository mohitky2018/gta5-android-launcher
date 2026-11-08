package io.github.playgta5.launcher.saves;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;

import io.github.playgta5.launcher.R;

public class StorySavesDialog extends Dialog {

    public interface ImportRequestListener {
        void onImportRequested();
    }

    private final StorySavesManager savesManager;
    private final ImportRequestListener importListener;
    private final List<StorySavesManager.SaveSlot> savesList = new ArrayList<>();
    private SavesAdapter adapter;

    public StorySavesDialog(@NonNull Context context, ImportRequestListener importListener) {
        super(context, R.style.Theme_GTA5Launcher);
        this.savesManager = new StorySavesManager(context);
        this.importListener = importListener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_story_saves);

        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        TextView textFolder = findViewById(R.id.text_saves_folder_path);
        textFolder.setText("Location: " + savesManager.getSavesDirectory().getAbsolutePath());

        ListView listView = findViewById(R.id.list_saves);
        adapter = new SavesAdapter();
        listView.setAdapter(adapter);

        reloadSaves();

        findViewById(R.id.btn_refresh_saves).setOnClickListener(v -> reloadSaves());

        findViewById(R.id.btn_import_save).setOnClickListener(v -> {
            if (importListener != null) {
                importListener.onImportRequested();
            }
            dismiss();
        });
    }

    public void reloadSaves() {
        savesList.clear();
        savesList.addAll(savesManager.listSaves());
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private class SavesAdapter extends BaseAdapter {
        @Override
        public int getCount() { return savesList.size(); }

        @Override
        public Object getItem(int position) { return savesList.get(position); }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_vehicle, parent, false);
            }

            StorySavesManager.SaveSlot slot = savesList.get(position);

            TextView textTitle = convertView.findViewById(R.id.text_vehicle_name);
            TextView textDesc = convertView.findViewById(R.id.text_vehicle_model);
            Button btnAction = convertView.findViewById(R.id.btn_spawn);

            textTitle.setText(slot.slotName);
            textDesc.setText(String.format("Modified: %s • %.1f KB", slot.getFormattedDate(), slot.fileSize / 1024f));
            btnAction.setText("DELETE");

            btnAction.setOnClickListener(v -> {
                new AlertDialog.Builder(getContext(), R.style.Theme_GTA5Launcher)
                        .setTitle("Delete Save")
                        .setMessage("Delete save slot " + slot.slotName + "?")
                        .setPositiveButton("Delete", (d, w) -> {
                            savesManager.deleteSave(slot);
                            reloadSaves();
                            Toast.makeText(getContext(), "Deleted " + slot.slotName, Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });

            return convertView;
        }
    }
}

