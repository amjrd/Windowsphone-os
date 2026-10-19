package com.example.metrolauncher

import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
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
    val backgroundColor: Color = WpTileBlue,
    val isSystemWidget: Boolean = false,
    val widgetType: String = ""
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WindowsPhoneLauncherScreen() {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(0) }

    val currentTime = remember {
        mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
    }

    val batteryLevel = remember {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        (level * 100 / scale)
    }

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

    val pinnedTiles = remember {
        mutableStateListOf<AppItem>().apply {
            add(AppItem("Phone", "com.android.dialer", backgroundColor = WpTileBlue))
            add(AppItem("Messaging", "com.android.mms", backgroundColor = WpTileGreen))
            add(AppItem("Battery", "", backgroundColor = WpTileOrange, isSystemWidget = true, widgetType = "battery"))
            add(AppItem("Settings", "launcher.settings", backgroundColor = WpTilePurple, isSystemWidget = true, widgetType = "settings"))
        }
    }

    var selectedAppForDialog by remember { mutableStateOf<AppItem?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WPBackground)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount < -30 && currentScreen == 0) {
                        currentScreen = 1
                    } else if (dragAmount > 30 && currentScreen == 1) {
                        currentScreen = 0
                    }
                }
            }
            .padding(16.dp)
    ) {
        when (currentScreen) {
            0 -> {
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
                                    .clickable { currentScreen = 1 }
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
                                batteryLevel = batteryLevel,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    if (tile.isSystemWidget) {
                                        if (tile.widgetType == "settings") {
                                            currentScreen = 2
                                        }
                                    } else {
                                        try {
                                            val launchIntent = context.packageManager.getLaunchIntentForPackage(tile.packageName)
                                            if (launchIntent != null) context.startActivity(launchIntent)
                                        } catch (_: Exception) {}
                                    }
                                },
                                onLongClick = {
                                    if (!tile.isSystemWidget) {
                                        selectedAppForDialog = tile
                                    }
                                }
                            )
                        }
                    }
                }
            }
            1 -> {
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
                                .clickable { currentScreen = 0 }
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
                                            if (!pinnedTiles.any { it.packageName == app.packageName }) {
                                                pinnedTiles.add(app)
                                            }
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
            2 -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.height(28.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "settings",
                            color = WpTextWhite,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Light
                        )
                        Text(
                            text = "← start",
                            color = WpTextWhite.copy(alpha = 0.6f),
                            fontSize = 16.sp,
                            modifier = Modifier
                                .clickable { currentScreen = 0 }
                                .padding(4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                        colors = CardDefaults.cardColors(containerColor = WpTileDark),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "Set as Default Launcher", color = WpTextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Open system home settings to make Windows Phone your default launcher.", color = Color.LightGray, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    val intent = Intent(Settings.ACTION_SETTINGS)
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                        colors = CardDefaults.cardColors(containerColor = WpTileDark),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "Android System Settings", color = WpTextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Access full device configuration and system permissions.", color = Color.LightGray, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        selectedAppForDialog?.let { app ->
            AlertDialog(
                onDismissRequest = { selectedAppForDialog = null },
                title = { Text(text = app.title, color = Color.White) },
                text = { Text(text = "Unpin this tile from Start?", color = Color.LightGray) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pinnedTiles.remove(app)
                            selectedAppForDialog = null
                        }
                    ) {
                        Text("Unpin", color = WpTileBlue)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FlipableMetroTile(
    appItem: AppItem,
    batteryLevel: Int,
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
            if (appItem.isSystemWidget) {
                if (appItem.widgetType == "battery") {
                    Text(text = "$batteryLevel%", color = WpTextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                } else {
                    Spacer(modifier = Modifier.size(32.dp))
                }
            } else {
                appItem.icon?.let { iconDrawable ->
                    Image(
                        bitmap = iconDrawable.toBitmap().asImageBitmap(),
                        contentDescription = appItem.title,
                        modifier = Modifier.size(32.dp)
                    )
                } ?: Spacer(modifier = Modifier.height(32.dp))
            }

            Text(
                text = appItem.title,
                color = WpTextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
