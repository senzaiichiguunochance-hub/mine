package com.example.deviceinfo

import android.Manifest
import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.Bundle
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // テスト用として、必要な実行時権限を自動で一括リクエストする
        requestTestPermissions()

        val infoTextView = findViewById<TextView>(R.id.infoTextView)
        val sb = StringBuilder()

        // 1. OS & ビルド詳細情報
        sb.append("=== 【1. OS & ビルド詳細情報】 ===\n")
        safeAppend(sb, "メーカー (Manufacturer)") { Build.MANUFACTURER }
        safeAppend(sb, "ブランド (Brand)") { Build.BRAND }
        safeAppend(sb, "モデル (Model)") { Build.MODEL }
        safeAppend(sb, "デバイス (Device)") { Build.DEVICE }
        safeAppend(sb, "プロダクト (Product)") { Build.PRODUCT }
        safeAppend(sb, "ボード (Board)") { Build.BOARD }
        safeAppend(sb, "ハードウェア名") { Build.HARDWARE }
        safeAppend(sb, "ディスプレイビルド") { Build.DISPLAY }
        safeAppend(sb, "フィンガープリント") { Build.FINGERPRINT }
        safeAppend(sb, "ブートローダー") { Build.BOOTLOADER }
        safeAppend(sb, "ベースバンド/ラジオ") { Build.RADIO }
        safeAppend(sb, "Androidバージョン") { Build.VERSION.RELEASE }
        safeAppend(sb, "APIレベル") { Build.VERSION.SDK_INT.toString() }
        safeAppend(sb, "ビルドID") { Build.ID }
        safeAppend(sb, "インクリメンタル") { Build.VERSION.INCREMENTAL }
        safeAppend(sb, "セキュリティパッチ") { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "N/A" }
        sb.append("\n")

        // 2. CPU & アーキテクチャ情報
        sb.append("=== 【2. CPU & アーキテクチャ】 ===\n")
        safeAppend(sb, "CPUコア数") { "${Runtime.getRuntime().availableProcessors()} コア" }
        safeAppend(sb, "対応ABIs (命令セット)") { Build.SUPPORTED_ABIS.joinToString(", ") }
        safeAppend(sb, "対応32bit ABIs") { Build.SUPPORTED_32_BIT_ABIS.joinToString(", ") }
        safeAppend(sb, "対応64bit ABIs") { Build.SUPPORTED_64_BIT_ABIS.joinToString(", ") }
        sb.append("\n")

        // 3. メモリ (RAM) 詳細
        sb.append("=== 【3. メモリ (RAM) 詳細】 ===\n")
        safeAppend(sb, "総RAM容量") {
            val actManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            String.format(Locale.US, "%.2f GB", memInfo.totalMem / (1024.0 * 1024.0 * 1024.0))
        }
        safeAppend(sb, "空きRAM容量") {
            val actManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            String.format(Locale.US, "%.2f GB", memInfo.availMem / (1024.0 * 1024.0 * 1024.0))
        }
        sb.append("\n")

        // 4. ストレージ詳細
        sb.append("=== 【4. ストレージ詳細】 ===\n")
        safeAppend(sb, "内部ストレージ総容量") {
            val stat = StatFs(Environment.getDataDirectory().path)
            String.format(Locale.US, "%.2f GB", (stat.blockCountLong * stat.blockSizeLong) / (1024.0 * 1024.0 * 1024.0))
        }
        safeAppend(sb, "内部ストレージ空き容量") {
            val stat = StatFs(Environment.getDataDirectory().path)
            String.format(Locale.US, "%.2f GB", (stat.availableBlocksLong * stat.blockSizeLong) / (1024.0 * 1024.0 * 1024.0))
        }
        sb.append("\n")

        // 5. バッテリー詳細
        sb.append("=== 【5. バッテリー詳細】 ===\n")
        safeAppend(sb, "バッテリー残量") {
            val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level != -1 && scale != -1) String.format(Locale.US, "%.0f%%", (level / scale.toFloat()) * 100) else "N/A"
        }
        safeAppend(sb, "バッテリー温度") {
            val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val temp = (batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
            "$temp °C"
        }
        sb.append("\n")

        // 6. ディスプレイ詳細
        sb.append("=== 【6. ディスプレイ詳細】 ===\n")
        safeAppend(sb, "解像度 (ピクセル)") {
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(metrics)
            "${metrics.widthPixels} x ${metrics.heightPixels}"
        }
        safeAppend(sb, "画面密度 (DPI)") {
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(metrics)
            "${metrics.densityDpi} dpi"
        }
        sb.append("\n")

        // 7. 通信キャリア & SIM情報 (要パーミッション)
        sb.append("=== 【7. キャリア & SIM情報】 ===\n")
        safeAppend(sb, "通信事業者名 (キャリア)") {
            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            telephonyManager.networkOperatorName.takeIf { it.isNotEmpty() } ?: "取得不可 / 未挿入"
        }
        safeAppend(sb, "SIM国コード") {
            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            telephonyManager.simCountryIso.takeIf { it.isNotEmpty() } ?: "N/A"
        }
        sb.append("\n")

        // 8. Bluetooth 詳細
        sb.append("=== 【8. Bluetooth詳細】 ===\n")
        safeAppend(sb, "Bluetooth状態") {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = bluetoothManager.adapter
            when {
                adapter == null -> "非搭載"
                adapter.isEnabled -> "有効 (ON)"
                else -> "無効 (OFF)"
            }
        }
        safeAppend(sb, "接続中デバイス数・名前") {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = bluetoothManager.adapter
            if (adapter != null && adapter.isEnabled) {
                // 接続されているデバイスを取得（要BLUETOOTH_CONNECT権限）
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    val bondedDevices = adapter.bondedDevices
                    val deviceNames = bondedDevices.joinToString(", ") { it.name ?: "Unknown" }
                    "ペアリング済み: [${deviceNames}]"
                } else {
                    "権限不足により取得スキップ"
                }
            } else {
                "Bluetoothがオフまたは未対応"
            }
        }
        sb.append("\n")

        // 9. インストール済みアプリ数
        sb.append("=== 【9. アプリケーション情報】 ===\n")
        safeAppend(sb, "インストールアプリ総数") {
            val apps = packageManager.getInstalledApplications(PackageManager.MATCH_ALL)
            "${apps.size} 個のアプリ"
        }
        sb.append("\n")

        // 10. 搭載センサー一覧
        sb.append("=== 【10. 搭載センサー一覧】 ===\n")
        safeAppend(sb, "センサー数") {
            val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
            "${sensors.size}個 (${sensors.joinToString(", ") { it.name }})"
        }

        // 画面に反映
        infoTextView.text = sb.toString()
    }

    // 実行時パーミッションを簡易的に要求するテスト用メソッド
    private fun requestTestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.BLUETOOTH_CONNECT
                ),
                100
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_PHONE_STATE),
                100
            )
        }
    }

    private fun safeAppend(sb: StringBuilder, label: String, block: () -> String) {
        try {
            val value = block()
            sb.append("$label: $value\n")
        } catch (e: Exception) {
            sb.append("$label: 取得エラー\n")
        }
    }
}