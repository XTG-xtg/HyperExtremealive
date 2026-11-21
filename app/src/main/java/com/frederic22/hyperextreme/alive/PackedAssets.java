package com.frederic22.hyperextreme.alive;

import android.content.res.AssetManager;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/** Streaming input for resources already extracted by the digest-pinned runtime caches. */
final class PackedAssets {
    // Kept in sync with tools/pack_alive_assets.py by the packaged-resource integration test.
    static final Set<String> PATHS = Set.of(
            "vivo/runtime/libpag.so",
            "vivo/runtime/libffavc.so");

    static InputStream open(AssetManager assets, String path) throws IOException {
        if (!PATHS.contains(path)) return assets.open(path);
        InputStream input = assets.open(path + ".agz");
        try {
            return new GZIPInputStream(input, 65536);
        } catch (IOException | RuntimeException error) {
            try { input.close(); } catch (IOException close) { error.addSuppressed(close); }
            throw error;
        }
    }

    private PackedAssets() {}
}
