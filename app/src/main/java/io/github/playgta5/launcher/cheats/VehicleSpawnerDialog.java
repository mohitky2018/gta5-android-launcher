package io.github.playgta5.launcher.cheats;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import io.github.playgta5.launcher.R;

public class VehicleSpawnerDialog extends Dialog {

    private final WebView webView;
    private final CheatsManager cheatsManager = CheatsManager.getInstance();

    private List<VehicleSpawner.VehicleInfo> displayList = new ArrayList<>();
    private VehicleAdapter adapter;
    private CheckBox checkWarpInside;

    public VehicleSpawnerDialog(@NonNull Context context, WebView webView) {
        super(context, R.style.Theme_GTA5Launcher);
        this.webView = webView;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_vehicle_spawner);

        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        checkWarpInside = findViewById(R.id.check_warp_inside);
        EditText editSearch = findViewById(R.id.edit_search_vehicle);
        Spinner spinner = findViewById(R.id.spinner_categories);
        ListView listView = findViewById(R.id.list_vehicles);

        displayList.addAll(VehicleSpawner.getAllVehicles());
        adapter = new VehicleAdapter();
        listView.setAdapter(adapter);

        List<String> categories = new ArrayList<>();
        categories.add("All Categories");
        categories.addAll(VehicleSpawner.getCategories());

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, categories);
        spinner.setAdapter(spinnerAdapter);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    displayList = new ArrayList<>(VehicleSpawner.getAllVehicles());
                } else {
                    displayList = new ArrayList<>(VehicleSpawner.getVehiclesByCategory(categories.get(position)));
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s == null || s.toString().trim().isEmpty()) {
                    displayList = new ArrayList<>(VehicleSpawner.getAllVehicles());
                } else {
                    displayList = VehicleSpawner.search(s.toString());
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private class VehicleAdapter extends BaseAdapter {
        @Override
        public int getCount() { return displayList.size(); }

        @Override
        public Object getItem(int position) { return displayList.get(position); }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_vehicle, parent, false);
            }

            VehicleSpawner.VehicleInfo item = displayList.get(position);

            TextView textName = convertView.findViewById(R.id.text_vehicle_name);
            TextView textModel = convertView.findViewById(R.id.text_vehicle_model);
            Button btnSpawn = convertView.findViewById(R.id.btn_spawn);

            textName.setText(item.displayName);
            textModel.setText(item.model + " • " + item.category);

            btnSpawn.setOnClickListener(v -> {
                boolean warp = checkWarpInside != null && checkWarpInside.isChecked();
                cheatsManager.spawnVehicle(webView, item.model, warp);
                dismiss();
            });

            return convertView;
        }
    }
}

