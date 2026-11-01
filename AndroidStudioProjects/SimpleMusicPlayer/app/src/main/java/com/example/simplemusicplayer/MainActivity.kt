package com.example.simplemusicplayer

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.text.Collator
import android.icu.text.RuleBasedCollator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var store: PlaybackStore

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pageReady = false
    private var pendingPlay = false
    private var errorMessage: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            pushState()
            handler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    private val audioExtensions = setOf("mp3", "m4a", "wav", "ogg", "aac", "flac")

    private val collator: Collator = Collator.getInstance().also {
        it.setStrength(Collator.PRIMARY)
        (it as RuleBasedCollator).setNumericCollation(true)
    }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

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

            store.treeUri = uri.toString()

            thread {
                val files = scanAudioFiles(uri)
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) applyFolder(files)
                }
            }
        }

    private val playerListener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) = pushPlaylist()

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            errorMessage = null
        }

        override fun onPlayerError(error: PlaybackException) {
            errorMessage = "再生できないファイルです。フォルダを選び直してください"
        }

        override fun onEvents(player: Player, events: Player.Events) = pushState()
    }

    private inner class Bridge {

        @JavascriptInterface
        fun onPageReady() = runOnUiThread {
            pageReady = true
            pushPlaylist()
            pushState()
        }

        @JavascriptInterface
        fun pickFolder() = runOnUiThread { openDirectory.launch(null) }

        @JavascriptInterface
        fun openCurrentFolder() = runOnUiThread {
            val treeUriStr = store.treeUri

            // フォルダが未選択の場合はピッカーを起動してあげる
            if (treeUriStr == null) {
                openDirectory.launch(null)
                return@runOnUiThread
            }

            var opened = false

            // 1. まずは選択されたフォルダを直接開くトライ
            try {
                val treeUri = Uri.parse(treeUriStr)
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                opened = true
            } catch (_: Exception) {
            }

            // 2. 直接オープンに失敗した場合は、ツリーURIそのもので開く（デフォルト/ルート付近にフォールバック）
            if (!opened) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(treeUriStr)).apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                    opened = true
                } catch (_: Exception) {
                }
            }

            // 3. それでもダメな場合の最終手段：ファイルアプリのトップやピッカーを開いて音を止めないようにする
            if (!opened) {
                try {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                }
            }
        }

        @JavascriptInterface
        fun refreshFolder() = runOnUiThread {
            val treeUriStr = store.treeUri
            if (treeUriStr == null) {
                errorMessage = "フォルダが選択されていません"
                pushState()
                return@runOnUiThread
            }

            thread {
                try {
                    val files = scanAudioFiles(Uri.parse(treeUriStr))
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        updateFilesSafely(files)
                    }
                } catch (_: Exception) {
                }
            }
        }

        @JavascriptInterface
        fun togglePlay() = runOnUiThread {
            val c = controller
            when {
                c == null || c.mediaItemCount == 0 -> openDirectory.launch(null)
                isPlaying(c) -> c.pause()
                else -> startPlayback(c)
            }
        }

        @JavascriptInterface
        fun playIndex(index: Int) = runOnUiThread {
            val c = controller ?: return@runOnUiThread
            if (index !in 0 until c.mediaItemCount) return@runOnUiThread
            if (index != c.currentMediaItemIndex) c.seekToDefaultPosition(index)
            startPlayback(c)
        }

        @JavascriptInterface
        fun prev() = runOnUiThread {
            val c = controller ?: return@runOnUiThread
            if (c.hasPreviousMediaItem()) c.seekToPreviousMediaItem() else c.seekTo(0)
            startPlayback(c)
        }

        @JavascriptInterface
        fun next() = runOnUiThread {
            val c = controller ?: return@runOnUiThread
            if (!c.hasNextMediaItem()) return@runOnUiThread
            c.seekToNextMediaItem()
            startPlayback(c)
        }

        @JavascriptInterface
        fun seekTo(positionMs: Double) = runOnUiThread {
            controller?.seekTo(positionMs.toLong().coerceAtLeast(0))
        }

        @JavascriptInterface
        fun seekBy(deltaMs: Double) = runOnUiThread {
            val c = controller ?: return@runOnUiThread
            val target = (c.currentPosition + deltaMs.toLong()).coerceAtLeast(0)
            val duration = c.duration
            c.seekTo(if (duration > 0) target.coerceAtMost(duration) else target)
        }

        @JavascriptInterface
        fun setSpeed(speed: Double) = runOnUiThread {
            store.speed = speed.toFloat()
            controller?.setPlaybackSpeed(speed.toFloat())
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PlaybackStore(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
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

    override fun onStart() {
        super.onStart()
        connectController()
        handler.post(ticker)
    }

    override fun onStop() {
        handler.removeCallbacks(ticker)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        super.onStop()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future

        future.addListener({
            if (controllerFuture !== future) return@addListener
            val c = try {
                future.get()
            } catch (_: Exception) {
                return@addListener
            }

            controller = c
            c.addListener(playerListener)
            if (c.mediaItemCount == 0 || pendingPlay) {
                loadPlaylist(c, play = pendingPlay)
                pendingPlay = false
            }
            pushPlaylist()
            pushState()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun isPlaying(c: MediaController) =
        c.playWhenReady && c.playbackState != Player.STATE_ENDED

    private fun startPlayback(c: MediaController) {
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
        c.play()
    }

    private fun applyFolder(files: List<AudioFile>) {
        if (files.isEmpty()) {
            errorMessage = "音声ファイルが見つかりませんでした"
            pushState()
            return
        }

        store.saveFiles(files)
        store.lastUri = null

        val c = controller
        if (c != null) loadPlaylist(c, play = true) else pendingPlay = true
    }

    private fun updateFilesSafely(files: List<AudioFile>) {
        if (files.isEmpty()) {
            errorMessage = "音声ファイルが見つかりませんでした"
            pushState()
            return
        }

        val c = controller
        val currentPlayingUri = if (c != null && c.mediaItemCount > 0 && c.currentMediaItemIndex in 0 until c.mediaItemCount) {
            c.getMediaItemAt(c.currentMediaItemIndex).mediaId
        } else {
            null
        }
        val isCurrentlyPlaying = c != null && isPlaying(c)

        store.saveFiles(files)

        if (c == null) return

        val items = files.map { file ->
            MediaItem.Builder()
                .setMediaId(file.uri)
                .setUri(file.uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(file.name).build())
                .build()
        }

        // 再生中のファイルがまだ存在する場合は、その位置を維持してプレイリストだけを安全に更新
        val newIndex = if (currentPlayingUri != null) {
            files.indexOfFirst { it.uri == currentPlayingUri }
        } else {
            -1
        }

        if (newIndex != -1 && isCurrentlyPlaying) {
            // 再生を中断・邪魔しないように、現在の再生状態・位置を保ったままプレイリスト（Timeline）のみを更新
            val currentPos = c.currentPosition
            c.setMediaItems(items, newIndex, currentPos)
            c.prepare()
            c.play()
        } else {
            // 再生中でない場合や、再生中のファイル自体が削除されていた場合
            val fallbackIndex = if (newIndex != -1) newIndex else 0
            val uriToRestore = files[fallbackIndex].uri
            c.setMediaItems(items, fallbackIndex, store.getPosition(uriToRestore))
            c.prepare()
        }

        errorMessage = null
        pushPlaylist()
        pushState()
    }

    private fun loadPlaylist(c: MediaController, play: Boolean) {
        val files = store.loadFiles()
        if (files.isEmpty()) return

        val items = files.map { file ->
            MediaItem.Builder()
                .setMediaId(file.uri)
                .setUri(file.uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(file.name).build())
                .build()
        }
        val index = files.indexOfFirst { it.uri == store.lastUri }.coerceAtLeast(0)

        errorMessage = null
        c.setMediaItems(items, index, store.getPosition(files[index].uri))
        c.prepare()
        if (play) c.play()
    }

    private fun pushPlaylist() {
        val c = controller ?: return
        val names = JSONArray()
        for (i in 0 until c.mediaItemCount) {
            names.put(c.getMediaItemAt(i).mediaMetadata.title?.toString() ?: "")
        }
        callJs("window.onPlaylist($names);")
    }

    private fun pushState() {
        val c = controller ?: return
        val state = JSONObject()
            .put("playing", isPlaying(c))
            .put("index", if (c.mediaItemCount == 0) -1 else c.currentMediaItemIndex)
            .put("position", c.currentPosition)
            .put("duration", c.duration.coerceAtLeast(0))
            .put("speed", c.playbackParameters.speed.toDouble())
            .put("error", errorMessage ?: JSONObject.NULL)
        callJs("window.onPlayerState($state);")
    }

    private fun callJs(script: String) {
        if (pageReady) webView.evaluateJavascript(script, null)
    }

    private fun scanAudioFiles(treeUri: Uri): List<AudioFile> {
        val files = mutableListOf<AudioFile>()
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

                    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    files.add(AudioFile(name, uri.toString()))
                }
            }
        } catch (_: Exception) {
        }
        return files.sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    private companion object {
        const val TICK_INTERVAL_MS = 500L
    }
}