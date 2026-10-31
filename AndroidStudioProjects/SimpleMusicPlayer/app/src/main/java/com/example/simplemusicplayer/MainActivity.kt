package com.example.simplemusicplayer

import android.annotation.SuppressLint
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    // フォルダ選択ランチャー（ACTION_OPEN_DOCUMENT_TREEを使用）
    private val openDirectory = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            // フォルダへのアクセス権限を永続化
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                // 例外無視
            }

            // 選択されたフォルダ内の音楽ファイルをスキャンしてJSONにする
            val jsonArray = scanAudioFilesFromTreeUri(uri)

            // JavaScript側に渡す
            val escapedJson = jsonArray.toString().replace("'", "\\'")
            webView.post {
                webView.evaluateJavascript("if(window.setAndroidFiles) { window.setAndroidFiles('$escapedJson'); }", null)
            }
            fileUploadCallback?.onReceiveValue(null)
        } else {
            // キャンセルされた場合も必ずリセットする（これで無反応を防ぐ）
            fileUploadCallback?.onReceiveValue(null)
        }
        fileUploadCallback = null
    }

    // フォルダ（TreeUri）の中を走査して音声ファイルを集める関数
    private fun scanAudioFilesFromTreeUri(treeUri: Uri): JSONArray {
        val jsonArray = JSONArray()
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

        val validExts = listOf("mp3", "m4a", "wav", "ogg", "aac", "flac")

        val cursor: Cursor? = contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null, null, null
        )

        cursor?.use {
            val idCol = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

            while (it.moveToNext()) {
                if (idCol == -1 || nameCol == -1) continue
                val fileId = it.getString(idCol)
                val fileName = it.getString(nameCol) ?: continue

                val ext = fileName.substringAfterLast('.', "").lowercase()
                if (validExts.contains(ext)) {
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, fileId)

                    val jsonObj = JSONObject().apply {
                        put("name", fileName)
                        put("uri", fileUri.toString())
                    }
                    jsonArray.put(jsonObj)
                }
            }
        }
        return jsonArray
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            webViewClient = WebViewClient()

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    callback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    // 古いコールバックが残っていればクリアして無反応を防ぐ
                    fileUploadCallback?.onReceiveValue(null)
                    fileUploadCallback = callback
                    try {
                        openDirectory.launch(null)
                    } catch (e: Exception) {
                        fileUploadCallback?.onReceiveValue(null)
                        fileUploadCallback = null
                        return false
                    }
                    return true
                }
            }
        }

        webView.loadUrl("file:///android_asset/index.html")
        setContentView(webView)
    }
}