package com.example.metrolauncher.model

import android.graphics.drawable.Drawable

data class AppTileModel(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    val tileSize: TileSize = TileSize.MEDIUM
)

enum class TileSize(val span: Int) {
    SMALL(1),
    MEDIUM(2),
    WIDE(4)
}
