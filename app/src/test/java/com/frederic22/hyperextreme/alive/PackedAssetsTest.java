package com.frederic22.hyperextreme.alive;

import android.app.Application;
import android.content.Context;
import android.content.res.AssetManager;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class PackedAssetsTest {
    private Context context() { return RuntimeEnvironment.getApplication(); }
    private static String hash(byte[] bytes) throws Exception {
        StringBuilder out = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes))
            out.append(String.format(Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }
    @Test public void generatedBundleMatchesAllowlistAndOriginalDigests() throws Exception {
        AssetManager assets = context().getAssets();
        JSONObject entries;
        try (InputStream in = assets.open("packed-alive-resources.json")) {
            entries = new JSONObject(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getJSONObject("assets");
        }
        Set<String> paths = new HashSet<>();
        entries.keys().forEachRemaining(paths::add);
        assertEquals(PackedAssets.PATHS, paths);
        for (String path : paths) {
            assertThrows(IOException.class, () -> assets.open(path));
            byte[] restored;
            try (InputStream in = PackedAssets.open(assets, path)) { restored = in.readAllBytes(); }
            assertEquals(entries.getJSONObject(path).getLong("bytes"), restored.length);
            assertEquals(entries.getJSONObject(path).getString("sha256"), hash(restored));
            assertTrue(entries.getJSONObject(path).getLong("gzip_bytes") < restored.length);
        }
    }
    @Test public void ordinaryAssetsRemainAvailableAndMissingAssetsFail() throws Exception {
        AssetManager assets = context().getAssets();
        try (InputStream original = assets.open("cosmic/catalog.json");
             InputStream routed = PackedAssets.open(assets, "cosmic/catalog.json")) {
            assertArrayEquals(original.readAllBytes(), routed.readAllBytes());
        }
        assertThrows(IOException.class, () -> PackedAssets.open(assets, "not-an-asset"));
    }
}
