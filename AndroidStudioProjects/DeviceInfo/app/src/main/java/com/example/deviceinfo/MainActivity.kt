package com.example.deviceinfo

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private data class Row(val label: String, val value: String)
    private data class Section(val title: String, val rows: List<Row>)

    private lateinit var container: LinearLayout
    private var sections: List<Section> = emptyList()

    // 許可結果が返った後は必ず onResume が呼ばれるので、再取得はそこで行う
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 端末がダークモードでも常にライトテーマで表示する
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = "デバイス情報"
        container = findViewById(R.id.container)

        // Android 12 以降のみ BLUETOOTH_CONNECT をリクエスト（それ以前は hasBluetoothPermission() が true）
        if (savedInstanceState == null && !hasBluetoothPermission()) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // ---------------------------------------------------------------- メニュー

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_REFRESH, 0, "更新").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu.add(0, MENU_COPY, 1, "コピー").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu.add(0, MENU_SHARE, 2, "共有").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        MENU_REFRESH -> { refresh(); true }
        MENU_COPY -> {
            val cm = getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("device_info", asText()))
            Toast.makeText(this, "コピーしました", Toast.LENGTH_SHORT).show()
            true
        }
        MENU_SHARE -> {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, asText())
            }
            startActivity(Intent.createChooser(send, "デバイス情報を共有"))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    // ---------------------------------------------------------------- 取得処理

    private fun refresh() {
        sections = listOf(
            section("OS・ビルド情報") { osRows() },
            section("CPU・アーキテクチャ") { cpuRows() },
            section("メモリ (RAM)") { memoryRows() },
            section("ストレージ") { storageRows() },
            section("バッテリー") { batteryRows() },
            section("ディスプレイ") { displayRows() },
            section("ネットワーク") { networkRows() },
            section("キャリア・SIM") { simRows() },
            section("Bluetooth") { bluetoothRows() },
            section("ハードウェア機能") { featureRows() },
            section("アプリ") { appRows() },
            section("センサー") { sensorRows() },
            section("言語・時刻・稼働時間") { systemRows() }
        )
        render()
    }

    private fun section(title: String, block: () -> List<Row>): Section =
        try {
            Section(title, block())
        } catch (e: Exception) {
            Section(title, listOf(Row("状態", "取得できません (${e.javaClass.simpleName})")))
        }

    private fun row(label: String, block: () -> String): Row =
        try {
            Row(label, block())
        } catch (e: Exception) {
            Row(label, "取得できません (${e.javaClass.simpleName})")
        }

    private fun osRows() = listOf(
        row("メーカー") { Build.MANUFACTURER },
        row("ブランド") { Build.BRAND },
        row("モデル") { Build.MODEL },
        row("デバイス") { Build.DEVICE },
        row("プロダクト") { Build.PRODUCT },
        row("ボード") { Build.BOARD },
        row("ハードウェア") { Build.HARDWARE },
        row("SoC") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}"
            else "N/A (Android 12 以降)"
        },
        row("Androidバージョン") { "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" },
        row("セキュリティパッチ") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "N/A"
        },
        row("ビルドID") { Build.ID },
        row("ビルド表示名") { Build.DISPLAY },
        row("インクリメンタル") { Build.VERSION.INCREMENTAL },
        row("ビルドタイプ") { Build.TYPE },
        row("ブートローダー") { Build.BOOTLOADER },
        row("ベースバンド") { Build.getRadioVersion().orEmpty().ifEmpty { "N/A" } },
        row("カーネル") { System.getProperty("os.version") ?: "N/A" },
        row("フィンガープリント") { Build.FINGERPRINT }
    )

    private fun cpuRows() = listOf(
        row("CPUコア数") { "${Runtime.getRuntime().availableProcessors()} コア" },
        row("対応ABI (優先順)") { Build.SUPPORTED_ABIS.joinToString(", ") },
        row("対応32bit ABI") { Build.SUPPORTED_32_BIT_ABIS.joinToString(", ").ifEmpty { "なし" } },
        row("対応64bit ABI") { Build.SUPPORTED_64_BIT_ABIS.joinToString(", ").ifEmpty { "なし" } },
        row("アプリが使える最大ヒープ") { "${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MB" }
    )

    private fun memoryRows(): List<Row> {
        val info = ActivityManager.MemoryInfo()
        getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        val used = info.totalMem - info.availMem
        return listOf(
            Row("総RAM容量", gb(info.totalMem)),
            Row("空きRAM容量", gb(info.availMem)),
            Row("使用率", String.format(Locale.US, "%.0f%%", used * 100.0 / info.totalMem)),
            Row("低メモリ状態", if (info.lowMemory) "はい" else "いいえ")
        )
    }

    private fun storageRows(): List<Row> {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val free = stat.availableBytes
        val used = total - free
        return listOf(
            Row("内部ストレージ総容量 (/data)", gb(total)),
            Row("空き容量", gb(free)),
            Row("使用量", gb(used)),
            Row("使用率", String.format(Locale.US, "%.0f%%", used * 100.0 / total))
        )
    }

    private fun batteryRows(): List<Row> {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return listOf(Row("バッテリー", "取得できません"))

        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = when (i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "充電中"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "放電中"
            BatteryManager.BATTERY_STATUS_FULL -> "満充電"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "充電していません"
            else -> "不明"
        }
        val plugged = when (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "ワイヤレス"
            0 -> "接続なし"
            else -> "その他"
        }
        val health = when (i.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "過熱"
            BatteryManager.BATTERY_HEALTH_DEAD -> "寿命"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "過電圧"
            BatteryManager.BATTERY_HEALTH_COLD -> "低温"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "異常"
            else -> "不明"
        }
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val volt = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)

        return listOf(
            Row("バッテリー残量", if (level >= 0 && scale > 0) "${level * 100 / scale}%" else "N/A"),
            Row("充電状態", status),
            Row("電源接続", plugged),
            Row("バッテリー状態", health),
            Row("温度", if (temp != Int.MIN_VALUE) String.format(Locale.US, "%.1f °C", temp / 10.0) else "N/A"),
            Row("電圧", if (volt > 0) "$volt mV" else "N/A"),
            Row("種類", i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "N/A")
        )
    }

    @Suppress("DEPRECATION")
    private fun displayRows(): List<Row> {
        val dm = resources.displayMetrics
        val (w, h) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = windowManager.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val m = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }
        val disp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay
        val cfg = resources.configuration
        return listOf(
            Row("解像度 (px)", "$w x $h"),
            Row("画面密度", "${dm.densityDpi} dpi (x${dm.density})"),
            Row("現在のウィンドウ (dp)", "${cfg.screenWidthDp} x ${cfg.screenHeightDp}"),
            Row("リフレッシュレート", disp?.let { String.format(Locale.US, "%.0f Hz", it.refreshRate) } ?: "N/A")
        )
    }

    private fun networkRows(): List<Row> {
        val cm = getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            ?: return listOf(Row("接続状態", "オフライン"))

        val type = listOfNotNull(
            "Wi-Fi".takeIf { caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) },
            "モバイル通信".takeIf { caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) },
            "有線LAN".takeIf { caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) },
            "Bluetooth".takeIf { caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) },
            "VPN".takeIf { caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) }
        ).joinToString(" + ").ifEmpty { "その他" }

        return listOf(
            Row("接続種別", type),
            Row("インターネット疎通", if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "確認済み" else "未確認"),
            Row("従量制課金", if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) "いいえ" else "はい"),
            Row("推定帯域 (下り / 上り)",
                String.format(Locale.US, "%.1f / %.1f Mbps",
                    caps.linkDownstreamBandwidthKbps / 1000.0, caps.linkUpstreamBandwidthKbps / 1000.0))
        )
    }

    private fun simRows(): List<Row> {
        val tm = getSystemService(TelephonyManager::class.java)
            ?: return listOf(Row("電話機能", "非搭載"))
        // これらの項目は READ_PHONE_STATE 権限なしで取得できる
        val simState = when (tm.simState) {
            TelephonyManager.SIM_STATE_ABSENT -> "SIMなし"
            TelephonyManager.SIM_STATE_READY -> "使用可能"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PINロック"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUKロック"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "ネットワークロック"
            else -> "不明 (${tm.simState})"
        }
        return listOf(
            Row("接続中のキャリア", tm.networkOperatorName.ifEmpty { "圏外 / 未接続" }),
            Row("SIM提供元", tm.simOperatorName.ifEmpty { "N/A" }),
            Row("SIM国コード", tm.simCountryIso.uppercase(Locale.US).ifEmpty { "N/A" }),
            Row("SIM状態", simState)
        )
    }

    @SuppressLint("MissingPermission")
    private fun bluetoothRows(): List<Row> {
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
            ?: return listOf(Row("Bluetooth状態", "非搭載"))

        val paired = when {
            !adapter.isEnabled -> "Bluetoothがオフです"
            !hasBluetoothPermission() -> "権限が許可されていません"
            else -> adapter.bondedDevices.let { d ->
                if (d.isEmpty()) "なし"
                else "${d.size}台\n" + d.joinToString("\n") { "・${it.name ?: "Unknown"}" }
            }
        }
        return listOf(
            Row("Bluetooth状態", if (adapter.isEnabled) "有効 (ON)" else "無効 (OFF)"),
            Row("BLE対応", featureLabel(PackageManager.FEATURE_BLUETOOTH_LE)),
            Row("ペアリング済みデバイス", paired)
        )
    }

    private fun featureRows() = listOf(
        row("Wi-Fi") { featureLabel(PackageManager.FEATURE_WIFI) },
        row("NFC") { featureLabel(PackageManager.FEATURE_NFC) },
        row("GPS") { featureLabel(PackageManager.FEATURE_LOCATION_GPS) },
        row("指紋センサー") { featureLabel(PackageManager.FEATURE_FINGERPRINT) },
        row("カメラ数") { "${getSystemService(CameraManager::class.java).cameraIdList.size} 台" }
    )

    @Suppress("DEPRECATION")
    private fun appRows(): List<Row> {
        // Android 11 以降はパッケージ可視性の制限があるため、ランチャーに表示されるアプリを数える
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            packageManager.queryIntentActivities(intent, 0)
        }
        val count = list.map { it.activityInfo.packageName }.distinct().size
        return listOf(Row("ランチャーに表示されるアプリ数", "$count 個"))
    }

    private fun sensorRows(): List<Row> {
        val sensors = getSystemService(SensorManager::class.java).getSensorList(Sensor.TYPE_ALL)
        return listOf(
            Row("センサー数", "${sensors.size} 個"),
            Row("一覧", sensors.joinToString("\n") { "・${it.name}" }.ifEmpty { "なし" })
        )
    }

    private fun systemRows() = listOf(
        row("言語・地域") { Locale.getDefault().toString() },
        row("タイムゾーン") {
            val tz = TimeZone.getDefault()
            "${tz.id} (${tz.getDisplayName(false, TimeZone.SHORT)})"
        },
        row("起動からの経過時間") {
            val min = SystemClock.elapsedRealtime() / 60_000
            "${min / 1440}日 ${(min % 1440) / 60}時間 ${min % 60}分"
        }
    )

    // ---------------------------------------------------------------- 描画

    private fun render() {
        container.removeAllViews()
        sections.forEach { container.addView(buildCard(it)) }
    }

    private fun buildCard(s: Section): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), COLOR_BORDER)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        card.addView(TextView(this).apply {
            text = s.title
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(COLOR_ACCENT)
            setPadding(0, 0, 0, dp(8))
        })
        s.rows.forEach { r ->
            card.addView(TextView(this).apply {
                text = r.label
                textSize = 12f
                setTextColor(COLOR_LABEL)
            })
            card.addView(TextView(this).apply {
                text = r.value
                textSize = 15f
                setTextColor(COLOR_VALUE)
                setTextIsSelectable(true)
                setPadding(0, dp(1), 0, dp(10))
            })
        }
        return card
    }

    // ---------------------------------------------------------------- ユーティリティ

    private fun asText(): String = sections.joinToString("\n\n") { s ->
        "■ ${s.title}\n" + s.rows.joinToString("\n") { "${it.label}: ${it.value}" }
    }

    private fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED

    private fun featureLabel(feature: String) =
        if (packageManager.hasSystemFeature(feature)) "対応" else "非対応"

    private fun gb(bytes: Long) =
        String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val MENU_REFRESH = 1
        private const val MENU_COPY = 2
        private const val MENU_SHARE = 3

        private val COLOR_ACCENT = Color.parseColor("#1976D2")
        private val COLOR_BORDER = Color.parseColor("#E0E4EA")
        private val COLOR_LABEL = Color.parseColor("#6B7785")
        private val COLOR_VALUE = Color.parseColor("#1F2933")
    }
}
