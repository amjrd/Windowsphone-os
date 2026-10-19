package com.example.metro

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

// ثيم ألوان ويندوز فون الكلاسيكي
val WPBackground = Color(0xFF000000)
val WpTileBlue = Color(0xFF0078D7)
val WpTileGreen = Color(0xFF107C10)
val WpTileOrange = Color(0xFFD83B01)
val WpTilePurple = Color(0xFF5C2D91)
val WpTileDark = Color(0xFF2D2D2D)
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

data.class AppItem(
    val title: String,
    val packageName: String,
    val backgroundColor: Color,
    val isWide: Boolean = false
)

@Composable
fun WindowsPhoneLauncherScreen() {
    val context = LocalContext.current
    val currentTime = remember {
        mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
    }

    // 1 & 4. قائمة التطبيقات والمربعات المثبتة (State Management & App Drawer base)
    var showAppDrawer by remember { mutableStateOf(false) }
    val pinnedTiles = remember {
        mutableStateListOf(
            AppItem("Phone", "com.android.dialer", WpTileBlue, true),
            AppItem("Messaging", "com.android.mms", WpTileGreen, false),
            AppItem("Store", "com.android.vending", WpTileOrange, false),
            AppItem("Settings", "com.android.settings", WpTilePurple, false)
        )
    }

    // جلب التطبيقات المثبتة الفعلية للجهاز
    val installedApps = remember {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos: List<ResolveInfo> = context.packageManager.queryIntentActivities(intent, 0)
        resolveInfos.map {
            AppItem(
                title = it.loadLabel(context.packageManager).toString(),
                packageName = it.activityInfo.packageName,
                backgroundColor = WpTileDark
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WPBackground)
            .padding(16.dp)
    ) {
        if (!showAppDrawer) {
            // شاشة البداية (Start Screen - Metro UI)
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
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "apps →",
                            color = WpTextWhite.copy(alpha = 0.7f),
                            fontSize = 18.sp,
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

                // شبكة المربعات الحية
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pinnedTiles) { tile ->
                        val spanVal = if (tile.isWide) 2 else 1
                        // استخدام نطاق للـ Grid span إذا لزم أو رسم المربع مباشرة
                        FlipableMetroTile(
                            title = tile.title,
                            backgroundColor = tile.backgroundColor,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // فتح التطبيق عند النقر
                            try {
                                val launchIntent = context.packageManager.getLaunchIntentForPackage(tile.packageName)
                                if (launchIntent != null) {
                                    context.startActivity(launchIntent)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } else {
            // شاشة قائمة التطبيقات (App Drawer - النمط الطولي لويندوز فون)
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
                        text = "← back",
                        color = WpTextWhite,
                        fontSize = 18.sp,
                        modifier = Modifier
                            .clickable { showAppDrawer = false }
                            .padding(4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(1),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(installedApps) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try {
                                        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                        if (intent != null) context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
    }
}

// 3. أنيميشن الانقلاب للمربعات (Tile Flip Animation)
@Composable
fun FlipableMetroTile(
    title: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var flipped by remember { mutableStateOf(false) }
    
    // أنيميشن الدوران ثلاثي الأبعاد
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(durationMillis = 600),
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
            .background(backgroundColor)
            .clickable {
                flipped = !flipped
                onClick()
            },
        contentAlignment = Alignment.BottomStart
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = title,
                color = WpTextWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiLogoTextIfExists ?: FontWeight.SemiBold
            )
            Text(
                text = if (rotation > 90f) "update..." else "metro live",
                color = WpTextWhite.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
        }
    }
}
