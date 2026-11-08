package io.github.playgta5.launcher.cheats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VehicleSpawner {

    public static class VehicleInfo {
        public final String model;
        public final String displayName;
        public final String category;

        public VehicleInfo(String model, String displayName, String category) {
            this.model = model;
            this.displayName = displayName;
            this.category = category;
        }
    }

    private static final List<VehicleInfo> VEHICLES = new ArrayList<>();
    private static final Map<String, List<VehicleInfo>> BY_CATEGORY = new HashMap<>();

    static {
        add("adder", "Truffade Adder", "Super");
        add("zentorno", "Pegassi Zentorno", "Super");
        add("t20", "Progen T20", "Super");
        add("osiris", "Pegassi Osiris", "Super");
        add("cheetah", "Grotti Cheetah", "Super");
        add("turismor", "Grotti Turismo R", "Super");
        add("entityxf", "Overflod Entity XF", "Super");

        add("elegy2", "Annis Elegy RH8", "Sports");
        add("banshee", "Bravado Banshee", "Sports");
        add("comet2", "Pfister Comet", "Sports");
        add("jester", "Dinka Jester", "Sports");
        add("massacro", "Dewbauchee Massacro", "Sports");
        add("futo", "Karin Futo", "Sports");
        add("feltzer2", "Benefactor Feltzer", "Sports");

        add("dominator", "Vapid Dominator", "Muscle");
        add("gauntlet", "Bravado Gauntlet", "Muscle");
        add("sabregt", "Declasse Sabre Turbo", "Muscle");
        add("phoenix", "Imponte Phoenix", "Muscle");
        add("buccaneer", "Albany Buccaneer", "Muscle");

        add("buzzard", "Nagasaki Buzzard Attack Chopper", "Helicopters");
        add("frogger", "Maibatsu Frogger", "Helicopters");
        add("maverick", "Buckingham Maverick", "Helicopters");
        add("cargobob", "Western Company Cargobob", "Helicopters");

        add("lazer", "P-996 LAZER Fighter Jet", "Planes");
        add("besra", "Western Company Besra", "Planes");
        add("titan", "Titan Cargo Plane", "Planes");
        add("mallard", "Western Company Mallard", "Planes");

        add("bati", "Pegassi Bati 801", "Motorcycles");
        add("akuma", "Dinka Akuma", "Motorcycles");
        add("sanchez", "Maibatsu Sanchez", "Motorcycles");
        add("hakuchou", "Shitzu Hakuchou", "Motorcycles");

        add("sandking", "Vapid Sandking XL", "Off-Road");
        add("dune", "BF Dune Buggy", "Off-Road");
        add("bifta", "BF Bifta", "Off-Road");

        add("dinghy", "Nagasaki Dinghy", "Boats");
        add("seashark", "Speedophile Seashark (Jet Ski)", "Boats");
        add("speeder", "Pegassi Speeder", "Boats");

        add("police", "Police Cruiser", "Emergency");
        add("ambulance", "Brute Ambulance", "Emergency");
        add("firetruk", "MTL Fire Truck", "Emergency");
        add("taxi", "Vapid Taxi", "Service");
    }

    private static void add(String model, String name, String category) {
        VehicleInfo info = new VehicleInfo(model, name, category);
        VEHICLES.add(info);
        List<VehicleInfo> list = BY_CATEGORY.computeIfAbsent(category, k -> new ArrayList<>());
        list.add(info);
    }

    public static List<VehicleInfo> getAllVehicles() {
        return Collections.unmodifiableList(VEHICLES);
    }

    public static List<String> getCategories() {
        return new ArrayList<>(BY_CATEGORY.keySet());
    }

    public static List<VehicleInfo> getVehiclesByCategory(String category) {
        return BY_CATEGORY.getOrDefault(category, Collections.emptyList());
    }

    public static List<VehicleInfo> search(String query) {
        String q = query.toLowerCase().trim();
        List<VehicleInfo> results = new ArrayList<>();
        for (VehicleInfo v : VEHICLES) {
            if (v.displayName.toLowerCase().contains(q) || v.model.toLowerCase().contains(q) || v.category.toLowerCase().contains(q)) {
                results.add(v);
            }
        }
        return results;
    }
}

