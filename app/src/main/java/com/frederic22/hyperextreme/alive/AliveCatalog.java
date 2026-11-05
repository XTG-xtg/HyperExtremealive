package com.frederic22.hyperextreme.alive;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

final class AliveCatalog {
    static final class Scene {
        final int id, group;
        final String name, thumbnail;
        Scene(int id, int group, String name, String thumbnail) {
            this.id = id; this.group = group; this.name = name; this.thumbnail = thumbnail;
        }
    }

    static List<Scene> load(Context context) throws Exception {
        List<Scene> scenes = new ArrayList<>();
        JSONArray cosmic = json(context, "cosmic/catalog.json");
        for (int i = 0; i < cosmic.length(); i++) {
            JSONObject row = cosmic.getJSONObject(i);
            int id = row.getInt("id");
            if (id >= 300) continue;
            // The upstream catalog also contains Bubble entries; never manufacture missing IDs.
            String directory = id >= 201 ? "bubble" : "cosmic";
            try (InputStream ignored = context.getAssets().open(directory + "/v" + id + ".json")) {
                scenes.add(new Scene(id, id >= 201 ? 1 : 0, row.getString("name"),
                    "cosmic/" + row.getString("thumbnail")));
            }
        }
        JSONArray gradients = json(context, "gradient/catalog.json");
        for (int i = 0; i < gradients.length(); i++) {
            JSONObject row = gradients.getJSONObject(i);
            scenes.add(new Scene(row.getInt("id"), 2, row.getString("name"),
                "cosmic/gradient_" + row.getString("slug") + ".png"));
        }
        JSONArray videos = json(context, "flyme-video/catalog.json");
        for (int i = 0; i < videos.length(); i++) {
            JSONObject row = videos.getJSONObject(i);
            scenes.add(new Scene(row.getInt("id"), 3, row.getString("name"),
                "flyme-video/" + row.getString("thumbnail")));
        }
        String[] names = {"山境", "丘壑", "层峦"};
        for (int i = 0; i < 3; i++) scenes.add(new Scene(501 + i, 4, names[i], "stereo/preview_v" + (4 + i) + ".png"));
        scenes.add(new Scene(601, 5, "圆景", ""));
        scenes.add(new Scene(602, 5, "留光", ""));
        return scenes;
    }

    private static JSONArray json(Context c, String path) throws Exception {
        try (InputStream in = c.getAssets().open(path); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return new JSONArray(out.toString("UTF-8"));
        }
    }
}
