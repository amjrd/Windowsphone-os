package com.example.metrolauncher

import android.accessibilityservice.AccessibilityService
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.database.Cursor
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.os.Bundle
import android.provider.CallLog
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class WpAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object {
        var instance: WpAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun lockScreen() {
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }
}

class MainActivity : ComponentActivity() {

    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appWidgetHost: AppWidgetHost

    private val selectWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val widgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
            if (widgetId != -1) {
                val appWidgetInfo = appWidgetManager.getAppWidgetInfo(widgetId)
                if (appWidgetInfo != null) {
                    onWidgetAddedListener?.invoke(widgetId, appWidgetInfo)
                }
            }
        }
    }

    var onWidgetAddedListener: ((Int, AppWidgetProviderInfo) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, 2026)
        appWidgetHost.startListening()

        setContent {
            WindowsPhoneTheme {
                WindowsPhoneLauncherScreen(
                    onPickWidget = { onAdded ->
                        onWidgetAddedListener = onAdded
                        val appWidgetId = appWidgetHost.allocateAppWidgetId()
                        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        }
                        selectWidgetLauncher.launch(pickIntent)
                    },
                    createWidgetView = { widgetId ->
                        appWidgetHost.createView(this, widgetId, appWidgetManager.getAppWidgetInfo(widgetId))
                    }
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            appWidgetHost.stopListening()
        } catch (_: Exception) {}
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

data class SystemWidgetHolder(
    val id: Int,
    val info: AppWidgetProviderInfo
)

data class AppItem(
    val title: String,
    val packageName: String,
    var backgroundColor: Color = WpTileBlue,
    var isWide: Boolean = false,
    val isSystemWidget: Boolean = false,
    val widgetType: String = ""
)

// دالة مساعدة لحفظ واسترجاع التخطيط محلياً عبر SharedPreferences
object TileStorageManager {
    private const val PREF_NAME = "wp_launcher_prefs"
    private const val KEY_TILES = "pinned_tiles_json"

    fun saveTiles(context: Context, tiles: List<AppItem>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (tile in tiles) {
            if (!tile.isSystemWidget) {
                val obj = JSONObject().apply {
                    put("title", tile.title)
                    put("packageName", tile.packageName)
                    put("isWide", tile.isWide)
                    put("color", tile.backgroundColor.value.toLong())
                }
                jsonArray.put(obj)
            }
        }
        prefs.edit().putString(KEY_TILES, jsonArray.toString()).apply()
    }

    fun loadTiles(context: Context): List<AppItem>? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_TILES, null) ?: return null
        try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<AppItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    AppItem(
                        title = obj.getString("title"),
                        packageName = obj.getString("packageName"),
                        isWide = obj.getBoolean("isWide"),
                        backgroundColor = Color(obj.getLong("color"))
                    )
                )
            }
            return list
        } catch (_: Exception) {
            return null
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WindowsPhoneLauncherScreen(
    onPickWidget: ((Int, AppWidgetProviderInfo) -> Unit) -> Unit,
    createWidgetView: (Int) -> AppWidgetHostView
) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val customWidgets = remember { mutableStateListOf<SystemWidgetHolder>() }

    val currentTime = remember {
        mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
    }

    val batteryLevel = remember {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        (level * 100 / scale)
    }

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

    val installedApps = remember {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos: List<ResolveInfo> = context.packageManager.queryIntentActivities(intent, 0)
        resolveInfos.map {
            AppItem(
                title = it.loadLabel(context.packageManager).toString(),
                packageName = it.activityInfo.packageName,
                backgroundColor = WpTileBlue,
                isWide = false
            )
        }.sortedBy { it.title.lowercase() }
    }

    val filteredApps = installedApps.filter {
        it.title.contains(searchQuery, ignoreCase = true)
    }

    val pinnedTiles = remember {
        val saved = TileStorageManager.loadTiles(context)
        if (saved != null && saved.isNotEmpty()) {
            mutableStateListOf<AppItem>().apply {
                add(AppItem("Phone", "com.android.dialer", WpTileBlue, true, true, "calls"))
                add(AppItem("Messaging", "com.android.mms", WpTileGreen, false, true, "sms"))
                add(AppItem("Battery", "", WpTileOrange, false, true, "battery"))
                add(AppItem("Lock Screen", "", WpTilePurple, false, true, "lock"))
                addAll(saved)
            }
        } else {
            mutableStateListOf<AppItem>().apply {
                add(AppItem("Phone", "com.android.dialer", WpTileBlue, true, true, "calls"))
                add(AppItem("Messaging", "com.android.mms", WpTileGreen, false, true, "sms"))
                add(AppItem("Battery", "", WpTileOrange, false, true, "battery"))
                add(AppItem("Lock Screen", "", WpTilePurple, false, true, "lock"))
                add(AppItem("Settings", "launcher.settings", WpTileDark, true, true, "settings"))
            }
        }
    }

    var selectedTileForCustomization by remember { mutableStateOf<AppItem?>(null) }

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
            // إيماءة النقر المزدوج في الخلفية لقفل الشاشة
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        WpAccessibilityService.instance?.lockScreen()
                    }
                )
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "+ widget",
                                color = WpTileBlue,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        onPickWidget { id, info ->
                                            customWidgets.add(SystemWidgetHolder(id, info))
                                        }
                                    }
                                    .padding(4.dp)
                            )
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
                            val appIcon = remember(tile.packageName) {
                                if (!tile.isSystemWidget) {
                                    try {
                                        context.packageManager.getApplicationIcon(tile.packageName)
                                    } catch (_: Exception) { null }
                                } else null
                            }

                            FlipableMetroTile(
                                appItem = tile,
                                appIcon = appIcon,
                                batteryLevel = batteryLevel,
                                missedCalls = missedCallsCount,
                                unreadSms = unreadSmsCount,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    if (tile.isSystemWidget) {
                                        when (tile.widgetType) {
                                            "settings" -> currentScreen = 2
                                            "lock" -> WpAccessibilityService.instance?.lockScreen()
                                            "calls" -> {
                                                try {
                                                    context.startActivity(Intent(Intent.ACTION_DIAL))
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
                                    selectedTileForCustomization = tile
                                }
                            )
                        }

                        items(
                            items = customWidgets,
                            span = { GridItemSpan(2) }
                        ) { widgetHolder ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(WpTileDark)
                                    .padding(4.dp)
                            ) {
                                AndroidView(
                                    factory = { createWidgetView(widgetHolder.id) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
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
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search apps...", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WpTileBlue,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = WpTextWhite,
                            unfocusedTextColor = WpTextWhite
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(1),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredApps) { app ->
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
                                                TileStorageManager.saveTiles(context, pinnedTiles)
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
                                description = "Make Windows Phone your primary home launcher.",
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                                    } catch (_: Exception) {}
                                }
                            )
                        }
                        item {
                            SystemSettingCard(
                                title = "Enable Accessibility Service",
                                description = "Grant permission for lock screen gestures (Double tap to lock).",
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                    } catch (_: Exception) {}
                                }
                            )
                        }
                        item {
                            SystemSettingCard(
                                title = "Display & Brightness",
                                description = "Manage screen orientation, timeout, and display settings.",
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                                    } catch (_: Exception) {}
                                }
                            )
                        }
                        item {
                            SystemSettingCard(
                                title = "Android Full Settings",
                                description = "Access full device configuration, accounts, and hardware settings.",
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

        // نافذة تخصيص المربع (تغيير الحجم، اللون، أو الإزالة)
        selectedTileForCustomization?.let { tile ->
            AlertDialog(
                onDismissRequest = { selectedTileForCustomization = null },
                title = { Text(text = tile.title, color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Customize Tile Layout & Color", color = Color.LightGray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                tile.isWide = !tile.isWide
                                TileStorageManager.saveTiles(context, pinnedTiles)
                                selectedTileForCustomization = null
                            }, colors = ButtonDefaults.buttonColors(containerColor = WpTileBlue)) {
                                Text(if (tile.isWide) "Make Normal" else "Make Wide")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(32.dp).background(WpTileBlue).clickable { tile.backgroundColor = WpTileBlue; TileStorageManager.saveTiles(context, pinnedTiles); selectedTileForCustomization = null })
                            Box(modifier = Modifier.size(32.dp).background(WpTileGreen).clickable { tile.backgroundColor = WpTileGreen; TileStorageManager.saveTiles(context, pinnedTiles); selectedTileForCustomization = null })
                            Box(modifier = Modifier.size(32.dp).background(WpTileOrange).clickable { tile.backgroundColor = WpTileOrange; TileStorageManager.saveTiles(context, pinnedTiles); selectedTileForCustomization = null })
                            Box(modifier = Modifier.size(32.dp).background(WpTilePurple).clickable { tile.backgroundColor = WpTilePurple; TileStorageManager.saveTiles(context, pinnedTiles); selectedTileForCustomization = null })
                        }
                    }
                },
                confirmButton = {
                    if (!tile.isSystemWidget) {
                        TextButton(
                            onClick = {
                                pinnedTiles.remove(tile)
                                TileStorageManager.saveTiles(context, pinnedTiles)
                                selectedTileForCustomization = null
                            }
                        ) {
                            Text("Unpin Tile", color = Color.Red)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedTileForCustomization = null }) {
                        Text("Close", color = Color.Gray)
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
    appIcon: Drawable?,
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
                    "lock" -> {
                        Text(text = "🔒 Lock", color = WpTextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    else -> {
                        Spacer(modifier = Modifier.size(24.dp))
                    }
                }
            } else {
                appIcon?.let { iconDrawable ->
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
