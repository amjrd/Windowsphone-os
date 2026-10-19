package com.example.metrolauncher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.metrolauncher.model.AppTileModel
import com.example.metrolauncher.model.TileSize

@Composable
fun MetroTile(
    app: AppTileModel,
    onClick: () -> Unit
) {
    val accentColor = Color(0xFF0078D7) // لون Windows Phone Accent الكلاسيكي

    Box(
        modifier = Modifier
            .aspectRatio(if (app.tileSize == TileSize.WIDE) 2f else 1f)
            .background(color = accentColor, shape = RoundedCornerShape(2.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Image(
            bitmap = app.icon.toBitmap().asImageBitmap(),
            contentDescription = app.label,
            modifier = Modifier
                .size(44.dp)
                .align(Alignment.Center)
        )

        Text(
            text = app.label,
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}
