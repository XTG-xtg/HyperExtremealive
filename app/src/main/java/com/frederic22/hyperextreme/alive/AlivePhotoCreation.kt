package com.frederic22.hyperextreme.alive

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Import privately and carry the new photo into the editor without publishing draft/applied settings. */
internal fun newAlivePhotoPreview(context: Context, uri: Uri?, preferredVariant: Int,
    importImage: (Context, Uri) -> String = PhotoMedia::importImage): Intent? {
    if (uri == null) return null
    val variant = preferredVariant.takeIf { it in 601..606 } ?: 601
    val name = listOf("圆景", "留光", "星月", "轻启·照片", "山脉", "银河")[variant - 601]
    val photo = PhotoOptions.defaults().media(0, importImage(context, uri))
    val preset = AlivePreset("", name, variant, Config.dark(context),
        Config.prefs(context).getBoolean("clock", false), photo, AliveRenderOptions.load(context, true))
    return Intent(context, AlivePreviewActivity::class.java)
        .putExtra("variant", variant).putExtra("name", name).putExtra("mode", 1)
        .putExtra("preset", preset.json())
}
