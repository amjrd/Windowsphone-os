package com.example.metrolauncher

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.database.Cursor
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.os.Bundle
import android.provider.CallLog
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // طلب صلاحيات النظام الحساسة لقراءة المكالمات والرسائل لعرضها في الـ Widgets
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_CONTACTS
            )
        )

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
    val isWide: Boolean = false,
    val isSystemWidget: Boolean = false,
    val widgetType: String = "" // "battery", "calls", "sms", "settings"
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WindowsPhoneLauncherScreen() {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(0) } // 0: Start, 1: All Apps, 2: System Settings Hub

    val currentTime = remember {
        mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
    }

    // قراءة نسبة البطارية من النظام
    val batteryLevel = remember {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        (level * 100 / scale)
    }

    // قراءة المكالمات الفائتة الحقيقية من نظام أندرويد
    val missedCallsCount = remember {
        var count = 0
        try {
            val cursor: Cursor? = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.TYPE),
                "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.NEW} = ?",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString(), "1"),
                null
            )
            count = cursor?.count ?: 0
            cursor?.close()
        } catch (_: Exception) {}
        count
    }

    // قراءة الرسائل القصيرة غير المقروءة الحقيقية من النظام
    val unreadSmsCount = remember {
        var count = 0
        try {
            val cursor: Cursor? = context.contentResolver.query(
                android.net.Uri.parse("content://sms/inbox"),
                arrayOf("_id"),
                "read = 0",
                null,
                null
            )
            count = cursor?.count ?: 0
            cursor?.close()
        } catch (_: Exception) {}
        count
    }

    // جلب التطبيقات المثبتة الفعلية
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
                backgroundColor = WpTileBlue,
                isWide = false
            )
        }.sortedBy { it.title.lowercase() }
    }

    // المربعات الحية المثبتة في شاشة البداية المرتبطة بالنظام
    val pinnedTiles = remember {
        mutableStateListOf<AppItem>().apply {
            add(AppItem("Phone", "com.android.dialer", backgroundColor = WpTileBlue, isWide = true, isSystemWidget = true, widgetType = "calls"))
            add(AppItem("Messaging", "com.android.mms", backgroundColor = WpTileGreen, isWide = false, isSystemWidget = true, widgetType = "sms"))
            add(AppItem("Battery", "", backgroundColor = WpTileOrange, isWide = false, isSystemWidget = true, widgetType = "battery"))
            add(AppItem("Settings", "launcher.settings", backgroundColor = WpTilePurple, isWide = true, isSystemWidget = true, widgetType = "settings"))
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
                // شاشة البداية (Start Screen)
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
                        items(
                            items = pinnedTiles,
                            span = { tile -> GridItemSpan(if (tile.isWide) 2 else 1) }
                        ) { tile ->
                            FlipableMetroTile(
                                appItem = tile,
                                batteryLevel = batteryLevel,
                                missedCalls = missedCallsCount,
                                unreadSms = unreadSmsCount,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    if (tile.isSystemWidget) {
                                        when (tile.widgetType) {
                                            "settings" -> currentScreen = 2
                                            "calls" -> {
                                                try {
                                                    val intent = Intent(Intent.ACTION_DIAL)
                                                    context.startActivity(intent)
                                                } catch (_: Exception) {}
                                            }
                                            "sms" -> {
                                                try {
                                                    val intent = Intent(Intent.ACTION_MAIN).apply {
                                                        addCategory(Intent.CATEGORY_DEFAULT)
                                                        type = "vnd.android-dir/mms-sms"
                                                    }
                                                    context.startActivity(intent)
                                                } catch (_: Exception) {}
                                            }
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
                // شاشة قائمة التطبيقات (All Apps Drawer)
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
                // مركز إعدادات وصلاحيات النظام الشامل (System Settings & Integration Hub)
                Column(modifier = Modifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.height(28.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "system hub",
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

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(1),
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            SystemSettingCard(
                                title = "Set Default Home Launcher",
                                description = "Open system home settings to make Windows Phone your primary launcher.",
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                                    } catch (_: Exception) {}
                                }
                            )
                        }
                        item {
                            SystemSettingCard(
                                title = "Display & Brightness",
                                description = "Configure screen timeout, brightness, and display settings.",
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                                    } catch (_: Exception) {}
                                }
                            )
                        }
                        item {
                            SystemSettingCard(
                                title = "Sound & Volume",
                                description = "Manage ringtones, media volumes, and device notifications.",
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
                                    } catch (_: Exception) {}
                                }
                            )
                        }
                        item {
                            SystemSettingCard(
                                title = "Full Android Settings",
                                description = "Access all system configurations, accounts, and hardware settings.",
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                    } catch (_: Exception) {}
                                }
                            )
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

@Composable
fun SystemSettingCard(title: String, description: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = WpTileDark),
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, color = WpTextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = description, color = Color.LightGray, fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FlipableMetroTile(
    appItem: AppItem,
    batteryLevel: Int,
    missedCalls: Int,
    unreadSms: Int,
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
                when (appItem.widgetType) {
                    "battery" -> {
                        Text(text = "$batteryLevel%", color = WpTextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    "calls" -> {
                        if (missedCalls > 0) {
                            Text(text = "$missedCalls missed", color = WpTextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Spacer(modifier = Modifier.size(24.dp))
                        }
                    }
                    "sms" -> {
                        if (unreadSms > 0) {
                            Text(text = "$unreadSms new", color = WpTextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Spacer(modifier = Modifier.size(24.dp))
                        }
                    }
                    else -> {
                        Spacer(modifier = Modifier.size(24.dp))
                    }
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
