package com.example.clipnote

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val authenticators =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleSendIntent(intent)

        setContent {
            ClipNoteTheme {
                MainScreen(
                    viewModel = viewModel,
                    canAuthenticate = { canAuthenticate() },
                    onRequestAuth = { title, onSuccess -> showBiometricPrompt(title, onSuccess) }
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isSecretMode.collect { secret ->
                    if (secret) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSendIntent(intent)
    }

    // Android 10以降はフォーカスがあるときだけクリップボードを読める
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) checkClipboard()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) viewModel.setSecretMode(false)
    }

    private fun handleSendIntent(intent: Intent?) {
        val received = intent ?: return
        if (received.action != Intent.ACTION_SEND || received.type?.startsWith("text/") != true) return
        received.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.let(viewModel::onSharedText)
    }

    private fun checkClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0) {
            viewModel.updateClipboard("", false)
            return
        }
        val text = clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
        val sensitive = clipboard.primaryClipDescription?.extras
            ?.getBoolean("android.content.extra.IS_SENSITIVE", false) ?: false
        viewModel.updateClipboard(text, sensitive)
    }

    private fun canAuthenticate(): Boolean =
        BiometricManager.from(this).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    private fun showBiometricPrompt(title: String, onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val silent = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                if (!silent) Toast.makeText(baseContext, errString, Toast.LENGTH_SHORT).show()
            }
        })

        // DEVICE_CREDENTIAL を許可する場合、ネガティブボタンは設定できない
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("本人確認のため認証を行ってください")
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }
}
