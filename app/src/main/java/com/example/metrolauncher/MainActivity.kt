package com.example.metro

import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WindowsPhoneTheme {
                WindowsPhoneLauncherScreen()
            }
        }
    }
}

val WPBackground = Color(0xFF000000)
val WpTileBlue = Color(0xFF0078D7)
val WpTileGreen = Color(0xFF107C10)
val WpTileOrange = Color(0xFFD83B01)
val WpTilePurple = Color(0xFF5C2D91)
val WpTileDark = Color(0xFF1F1F1F)
val WpTextWhite = Color(0xFFFFFFFF)

@Composable
fun WindowsPhoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = WPBackground,
            surface = WpTileDark,
            onBackground = WpTextWhite,
            onSurface = WpTextWhite
        ),
        content = content
    )
}

data class AppItem(
    val title: String,
    val packageName: String,
    val icon: Drawable? = null,
    val backgroundColor: Color = WpTileBlue
)

@Composable
fun WindowsPhoneLauncherScreen() {
    val context = LocalContext.current
    val currentTime = remember {
        mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
    }

    var showAppDrawer by remember { mutableStateOf(false) }

    // جلب قائمة كافة التطبيقات المثبتة مع أيقوناتها الحقيقية (1. App Icons)
    val installedApps = remember {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos: List<ResolveInfo> = context.packageManager.queryIntentActivities(intent, 0)
        resolveInfos.map {
            AppItem(
                title = it.loadLabel(context.packageManager).toString(),
                packageName = it.activityInfo.packageName,
                icon = it.loadIcon(context.packageManager),
                backgroundColor = WpTileBlue
            )
        }.sortedBy { it.title.lowercase() }
    }

    // 3. إدارة التطبيقات المثبتة في شاشة البداية (Pin / Unpin State)
    val pinnedTiles = remember {
        mutableStateListOf<AppItem>().apply {
            // تثبيت أول 4 تطبيقات افتراضياً إذا كانت موجودة
            addAll(installedApps.take(6))
        }
    }

    var selectedAppForDialog by remember { mutableStateOf<AppItem?>(null) }
    var isUnpinDialog by remember { mutableStateOf(false) }

    // 2. السحب الذكي للأجناب (Swipe Gesture support)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WPBackground)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount < -30) {
                        showAppDrawer = true // سحب لليسار لفتح القائمة
                    } else if (dragAmount > 30) {
                        showAppDrawer = false // سحب لليمن للعودة لشاشة start
                    }
                }
            }
            .padding(16.dp)
    ) {
        if (!showAppDrawer) {
            // ----- شاشة البداية (Start Screen) -----
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "start",
                        color = WpTextWhite,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Light
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "apps →",
                            color = WpTextWhite.copy(alpha = 0.6f),
                            fontSize = 16.sp,
                            modifier = Modifier
                                .clickable { showAppDrawer = true }
                                .padding(4.dp)
                        )
                        Text(
                            text = currentTime.value,
                            color = WpTextWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Light
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pinnedTiles) { tile ->
                        FlipableMetroTile(
                            appItem = tile,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                try {
                                    val launchIntent = context.packageManager.getLaunchIntentForPackage(tile.packageName)
                                    if (launchIntent != null) context.startActivity(launchIntent)
                                } catch (_: Exception) {}
                            },
                            onLongClick = {
                                selectedAppForDialog = tile
                                isUnpinDialog = true
                            }
                        )
                    }
                }
            }
        } else {
            // ----- شاشة قائمة التطبيقات (All Apps Drawer) -----
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "all apps",
                        color = WpTextWhite,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Light
                    )
                    Text(
                        text = "← start",
                        color = WpTextWhite.copy(alpha = 0.6f),
                        fontSize = 16.sp,
                        modifier = Modifier
                            .clickable { showAppDrawer = false }
                            .padding(4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(1),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(installedApps) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .combinedClickable(
                                    onClick = {
                                        try {
                                            val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                            if (intent != null) context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    },
                                    onLongClick = {
                                        selectedAppForDialog = app
                                        isUnpinDialog = false
                                    }
                                )
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            app.icon?.let { iconDrawable ->
                                Image(
                                    bitmap = iconDrawable.toBitmap().asImageBitmap(),
                                    contentDescription = app.title,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = app.title,
                                color = WpTextWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // 3. نافذة التأكيد للـ Pin / Unpin
        selectedAppForDialog?.let { app ->
            AlertDialog(
                onDismissRequest = { selectedAppForDialog = null },
                title = { Text(text = app.title, color = Color.White) },
                text = {
                    Text(
                        text = if (isUnpinDialog) "Unpin this tile from Start?" else "Pin this app to Start?",
                        color = Color.LightGray
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (isUnpinDialog) {
                                pinnedTiles.remove(app)
                            } else {
                                if (!pinnedTiles.contains(app)) {
                                    pinnedTiles.add(app)
                                }
                            }
                            selectedAppForDialog = null
                        }
                    ) {
                        Text(if (isUnpinDialog) "Unpin" else "Pin to Start", color = WpTileBlue)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedAppForDialog = null }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = WpTileDark
            )
        }
    }
}

// المربع الحي المدعوم بـ Flip Animation والأيقونة المباشرة
@Composable
fun FlipableMetroTile(
    appItem: AppItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var flipped by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "tileFlip"
    )

    Box(
        modifier = modifier
            .height(110.dp)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 8f * density
            }
            .clip(RoundedCornerShape(0.dp))
            .background(appItem.backgroundColor)
            .combinedClickable(
                onClick = {
                    flipped = !flipped
                    onClick()
                },
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.BottomStart
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            // أيقونة التطبيق الحقيقية (1. App Icon)
            appItem.icon?.let { iconDrawable ->
                Image(
                    bitmap = iconDrawable.toBitmap().asImageBitmap(),
                    contentDescription = appItem.title,
                    modifier = Modifier.size(32.dp)
                )
            } ?: Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = appItem.title,
                color = WpTextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
