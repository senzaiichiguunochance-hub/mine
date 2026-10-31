package com.example.simplemusicplayer

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView

    private val audioExtensions = setOf("mp3", "m4a", "wav", "ogg", "aac", "flac")

    private val openDirectory =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@registerForActivityResult

            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }

            thread {
                val json = scanAudioFiles(uri).toString()
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    webView.evaluateJavascript(
                        "window.setAndroidFiles && window.setAndroidFiles(${JSONObject.quote(json)});",
                        null
                    )
                }
            }
        }

    private inner class Bridge {
        @JavascriptInterface
        fun pickFolder() {
            runOnUiThread { openDirectory.launch(null) }
        }
    }

    private fun scanAudioFiles(treeUri: Uri): JSONArray {
        val result = JSONArray()
        val folderId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )

        try {
            contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(0)
                    val name = cursor.getString(1) ?: continue
                    if (name.substringAfterLast('.', "").lowercase() !in audioExtensions) continue

                    result.put(JSONObject().apply {
                        put("name", name)
                        put("uri", DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId).toString())
                    })
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowContentAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
            addJavascriptInterface(Bridge(), "AndroidBridge")
        }

        ViewCompat.setOnApplyWindowInsetsListener(webView) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setContentView(webView)
        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
