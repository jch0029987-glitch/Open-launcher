package com.jeremy.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            var updateStatus by remember { mutableStateOf("Checking for updates...") }
            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                scope.launch {
                    val currentVersionCode = 1 // Match your current release versionCode
                    val updateInfo = checkForUpdates(currentVersionCode)
                    if (updateInfo != null) {
                        updateStatus = "Downloading update v${updateInfo.versionName}..."
                        val success = downloadAndInstallUpdate(context, updateInfo.downloadUrl)
                        if (!success) {
                            updateStatus = "TV Launcher"
                        }
                    } else {
                        updateStatus = "TV Launcher"
                    }
                }
            }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = updateStatus,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    }
}

data class UpdateInfo(val versionName: String, val downloadUrl: String)

suspend fun checkForUpdates(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
    try {
        val jsonString = URL("https://raw.githubusercontent.com/jch0029987-glitch/Open-launcher/main/version.json").readText()
        val json = JSONObject(jsonString)
        val remoteVersionCode = json.getInt("versionCode")
        
        if (remoteVersionCode > currentVersionCode) {
            UpdateInfo(
                versionName = json.getString("versionName"),
                downloadUrl = json.getString("downloadUrl")
            )
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
            apkFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        withContext(Dispatchers.Main) {
            context.startActivity(intent)
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
