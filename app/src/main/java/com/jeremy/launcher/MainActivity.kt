package com.jeremy.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

enum class AppScreen {
    HOME, SETTINGS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val context = LocalContext.current
            var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
            var updateStatus by remember { mutableStateOf("TV Launcher") }
            var appsList by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
            var wallpaperUrl by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    appsList = getLauncherApps(context)
                }

                scope.launch {
                    val currentVersionCode = 1
                    val updateInfo = checkForUpdates(currentVersionCode)
                    if (updateInfo != null) {
                        updateStatus = "Updating to v${updateInfo.versionName}..."
                        downloadAndInstallUpdate(context, updateInfo.downloadUrl)
                    }
                }
            }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        WallpaperEngineBackground(wallpaperUrl = wallpaperUrl)

                        when (currentScreen) {
                            AppScreen.HOME -> {
                                HomeScreen(
                                    updateStatus = updateStatus,
                                    appsList = appsList,
                                    onOpenSettings = { currentScreen = AppScreen.SETTINGS },
                                    onAppClick = { packageName ->
                                        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                                        if (launchIntent != null) {
                                            context.startActivity(launchIntent)
                                        }
                                    }
                                )
                            }
                            AppScreen.SETTINGS -> {
                                SettingsScreen(
                                    onBack = { currentScreen = AppScreen.HOME },
                                    onCheckUpdates = {
                                        scope.launch {
                                            updateStatus = "Checking for updates..."
                                            val updateInfo = checkForUpdates(1)
                                            if (updateInfo != null) {
                                                updateStatus = "Updating to v${updateInfo.versionName}..."
                                                downloadAndInstallUpdate(context, updateInfo.downloadUrl)
                                            } else {
                                                updateStatus = "App is up to date!"
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    updateStatus: String,
    appsList: List<AppInfo>,
    onOpenSettings: () -> Unit,
    onAppClick: (String) -> Unit
) {
    val firstItemFocusRequester = remember { FocusRequester() }

    // Grab focus into the grid as soon as apps are loaded, so the D-pad
    // drives item-to-item navigation instead of falling through to the window.
    LaunchedEffect(appsList) {
        if (appsList.isNotEmpty()) {
            firstItemFocusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(36.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Applications",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = updateStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.width(20.dp))
                SettingsButton(onClick = onOpenSettings)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxSize()
                .focusGroup()
        ) {
            itemsIndexed(appsList, key = { _, app -> app.packageName }) { index, app ->
                AppCard(
                    app = app,
                    modifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                    onClick = { onAppClick(app.packageName) }
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onCheckUpdates: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(50.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(10.dp))

        SettingsCard(title = "Check for Updates Now", subtitle = "Manually query GitHub repository for new APK releases") {
            onCheckUpdates()
        }

        SettingsCard(title = "Wallpaper Engine", subtitle = "Current Mode: Default Gradient Background") {}

        SettingsCard(title = "About Open Launcher", subtitle = "Version 1.0 (API 34 Leanback)") {}

        Spacer(modifier = Modifier.height(10.dp))

        SettingsCard(title = "Back to Home", subtitle = "Return to app launcher grid") {
            onBack()
        }
    }
}

@Composable
fun SettingsButton(onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = if (isFocused) Color.Cyan else Color.Transparent
    val background = if (isFocused) Color(0xCC3A3A3C) else Color(0x991E1E1E)

    Box(
        modifier = Modifier
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .border(2.dp, borderColor, MaterialTheme.shapes.small)
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(text = "Settings", color = Color.White, fontSize = 14.sp)
    }
}

@Composable
fun SettingsCard(title: String, subtitle: String, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = if (isFocused) Color.Cyan else Color.Transparent
    val background = if (isFocused) Color(0xCC3A3A3C) else Color(0x991E1E1E)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .border(2.dp, borderColor, MaterialTheme.shapes.medium)
            .clip(MaterialTheme.shapes.medium)
            .background(background)
            .clickable { onClick() }
            .padding(20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Text(text = title, color = Color.White, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, color = Color.LightGray, fontSize = 12.sp)
        }
    }
}

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: android.graphics.drawable.Drawable
)

@Composable
fun WallpaperEngineBackground(wallpaperUrl: String?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1F1C2C), Color(0xFF928DAB), Color(0xFF121212))
                )
            )
    )
}

@Composable
fun AppCard(app: AppInfo, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = if (isFocused) Color.Cyan else Color.Transparent
    val backgroundColor = if (isFocused) Color(0xCC3A3A3C) else Color(0x991E1E1E)

    Box(
        modifier = modifier
            .size(130.dp)
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .border(3.dp, borderColor, MaterialTheme.shapes.medium)
            .clip(MaterialTheme.shapes.medium)
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val bitmap = remember(app.icon) { app.icon.toBitmap(80, 80).asImageBitmap() }
            Image(
                bitmap = bitmap,
                contentDescription = app.label,
                modifier = Modifier.size(60.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 1
            )
        }
    }
}

fun getLauncherApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    val resolveInfos = pm.queryIntentActivities(intent, 0)
    return resolveInfos.map {
        AppInfo(
            label = it.loadLabel(pm).toString(),
            packageName = it.activityInfo.packageName,
            icon = it.loadIcon(pm)
        )
    }.sortedBy { it.label }
}

data class UpdateInfo(val versionName: String, val downloadUrl: String)

suspend fun checkForUpdates(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
    try {
        val jsonString = URL("https://raw.githubusercontent.com/jch0029987-glitch/Open-launcher/main/version.json").readText()
        val json = JSONObject(jsonString)
        val remoteVersionCode = json.getInt("versionCode")
        if (remoteVersionCode > currentVersionCode) {
            UpdateInfo(json.getString("versionName"), json.getString("downloadUrl"))
        } else null
    } catch (e: Exception) {
        null
    }
}

suspend fun downloadAndInstallUpdate(context: Context, downloadUrl: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val apkFile = File(context.cacheDir, "update.apk")
        if (apkFile.exists()) apkFile.delete()
        URL(downloadUrl).openStream().use { input ->
            apkFile.outputStream().use { output -> input.copyTo(output) }
        }
        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        withContext(Dispatchers.Main) { context.startActivity(intent) }
        true
    } catch (e: Exception) {
        false
    }
}
