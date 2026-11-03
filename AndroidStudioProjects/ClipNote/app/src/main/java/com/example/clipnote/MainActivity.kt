package com.example.clipnote

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.fragment.app.FragmentActivity // ※インポートに追加

class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.initData(this)

        setContent {
            val LightOrangeColorScheme = lightColorScheme(
                primary = Color(0xFFE65100),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFFFCC80),
                secondary = Color(0xFFF57C00),
                surface = Color(0xFFFFFBFE),
                background = Color(0xFFFFF8E1)
            )

            MaterialTheme(colorScheme = LightOrangeColorScheme) {
                MainScreen(
                    viewModel = viewModel,
                    onRequestBiometric = { onSuccess ->
                        showBiometricPrompt(onSuccess)
                    }
                )
            }
        }

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkClipboard()
            }
        })
    }

    override fun onStop() {
        super.onStop()
        viewModel.saveData(this)
    }

    private fun checkClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip()) {
            val item = clipboard.primaryClip?.getItemAt(0)
            val text = item?.text?.toString() ?: ""
            viewModel.updateClipboardText(text)
        }
    }

    // 生体認証（顔・指紋認証）ダイアログの表示
    private fun showBiometricPrompt(onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(baseContext, "認証がキャンセルされました", Toast.LENGTH_SHORT).show()
                }
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(baseContext, "認証に失敗しました", Toast.LENGTH_SHORT).show()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("シークレットモード認証")
            .setSubtitle("本人確認のため、生体認証を行ってください")
            .setNegativeButtonText("キャンセル")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestBiometric: (onSuccess: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    val items = viewModel.getSortedItems()
    val rawCount = viewModel.items.value.size
    val currentClipText by viewModel.currentClipboardText
    val sortOrder by viewModel.sortOrder
    val showConfirmDialog by viewModel.showConfirmDialog
    val isSecretMode by viewModel.isSecretMode
    val targetScrollIndex by viewModel.targetScrollIndex

    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ClipItem?>(null) }
    var showLimitAlert by remember { mutableStateOf(false) }

    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmMessage by remember { mutableStateOf("") }
    var confirmDefaultNo by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(targetScrollIndex) {
        targetScrollIndex?.let { index ->
            listState.animateScrollToItem(index)
            viewModel.clearScrollTarget()
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (rawCount >= 10000) {
                        showLimitAlert = true
                    } else {
                        showAddDialog = true
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "追加")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // --- 画面上部エリア ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "クリップボード: $currentClipText",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            Button(
                                onClick = { expanded = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Text(text = getSortLabel(sortOrder), color = Color.Black)
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(getSortLabel(order)) },
                                        onClick = {
                                            viewModel.setSortOrder(order)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Text(text = "$rawCount / 10000")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = showConfirmDialog,
                                onCheckedChange = { viewModel.setConfirmDialog(it) }
                            )
                            Text(text = "確認メッセージを表示する", style = MaterialTheme.typography.bodySmall)
                        }

                        // シークレットモードスイッチ（OFF -> ONのときに生体認証を要求）
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = "シークレット", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Checkbox(
                                checked = isSecretMode,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        // OFF から ON にするときは生体認証を行う
                                        onRequestBiometric {
                                            viewModel.setSecretMode(true)
                                        }
                                    } else {
                                        // ON から OFF はそのまま切り替え可能
                                        viewModel.setSecretMode(false)
                                    }
                                }
                            )
                            Text(text = "シークレットモード", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // --- メインリストエリア ---
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(items, key = { item -> item.id }) { item ->
                    ClipRow(
                        item = item,
                        isSecretMode = isSecretMode,
                        onDelete = {
                            // シークレットかつOFFのときは個別削除不可だが全削除の対象には含む
                            if (item.isSecret && !isSecretMode) {
                                Toast.makeText(context, "シークレットモードがOFFのため削除できません", Toast.LENGTH_SHORT).show()
                                return@ClipRow
                            }
                            val action = { viewModel.deleteItem(item.id) }
                            if (showConfirmDialog) {
                                confirmMessage = "この項目を削除しますか？"
                                confirmDefaultNo = false
                                pendingAction = action
                            } else {
                                action()
                            }
                        },
                        onDeleteAll = {
                            confirmMessage = "リストを全削除しますか？（シークレット項目も含む）"
                            confirmDefaultNo = true
                            pendingAction = { viewModel.deleteAll() }
                        },
                        onEdit = {
                            if (item.isSecret && !isSecretMode) {
                                Toast.makeText(context, "シークレットモードがOFFのため編集できません", Toast.LENGTH_SHORT).show()
                                return@ClipRow
                            }
                            editingItem = item
                        },
                        onCopy = {
                            if (item.isSecret && !isSecretMode) {
                                Toast.makeText(context, "シークレットモードがOFFのためコピーできません", Toast.LENGTH_SHORT).show()
                                return@ClipRow
                            }
                            val action = {
                                val clip = ClipData.newPlainText("ClipNote", item.text)
                                clipboardManager.setPrimaryClip(clip)
                                viewModel.updateClipboardText(item.text)
                                Toast.makeText(context, "コピーしました", Toast.LENGTH_SHORT).show()
                            }
                            if (showConfirmDialog) {
                                confirmMessage = "クリップボードにセットしますか？"
                                confirmDefaultNo = false
                                pendingAction = action
                            } else {
                                action()
                            }
                        }
                    )
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                }
            }
        }
    }

    if (showAddDialog) {
        TextInputDialog(
            title = "テキストの追加",
            initialText = "",
            initialIsSecret = false,
            onConfirm = { text, isSecret ->
                viewModel.addItem(text, isSecret) { showLimitAlert = true }
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    editingItem?.let { item ->
        TextInputDialog(
            title = "テキストの編集",
            initialText = item.text,
            initialIsSecret = item.isSecret,
            onConfirm = { newText, _ ->
                viewModel.updateItem(item.id, newText)
                editingItem = null
            },
            onDismiss = { editingItem = null }
        )
    }

    if (showLimitAlert) {
        AlertDialog(
            onDismissRequest = { showLimitAlert = false },
            title = { Text("上限に達しました") },
            text = { Text("上限（10,000件）に達しました。不要なリストを削除してください。") },
            confirmButton = {
                TextButton(onClick = { showLimitAlert = false }) { Text("OK") }
            }
        )
    }

    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text("確認") },
            text = { Text(confirmMessage) },
            confirmButton = {
                TextButton(onClick = {
                    action()
                    pendingAction = null
                }) { Text(if (confirmDefaultNo) "はい" else "OK") }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) { Text(if (confirmDefaultNo) "いいえ" else "キャンセル") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipRow(
    item: ClipItem,
    isSecretMode: Boolean,
    onDelete: () -> Unit,
    onDeleteAll: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit
) {
    // シークレットかつモードOFFのときはテキストを * でマスク表示
    val displayText = if (item.isSecret && !isSecretMode) {
        "•".repeat(item.text.length.coerceAtLeast(6))
    } else {
        item.text
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onCopy,
                onDoubleClick = onCopy
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onDelete,
            modifier = Modifier.combinedCardClickableCompat(
                onClick = onDelete,
                onLongClick = onDeleteAll
            )
        ) {
            Icon(Icons.Default.Close, contentDescription = "削除", tint = Color.Gray)
        }

        Text(
            text = displayText,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            maxLines = 3,
            color = if (item.isSecret && !isSecretMode) Color.Gray else Color.Unspecified
        )

        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "編集", tint = Color.Gray)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.combinedCardClickableCompat(
    onClick: () -> Unit,
    onLongClick: () -> Unit
): Modifier = this.combinedClickable(
    onClick = onClick,
    onLongClick = onLongClick
)

@Composable
fun TextInputDialog(
    title: String,
    initialText: String,
    initialIsSecret: Boolean,
    onConfirm: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var textState by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialText,
                selection = TextRange(initialText.length)
            )
        )
    }
    var isSecret by remember { mutableStateOf(initialIsSecret) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    maxLines = 5
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isSecret,
                        onCheckedChange = { isSecret = it }
                    )
                    Text(text = "シークレット（秘密の文字）として登録", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(textState.text, isSecret) },
                enabled = textState.text.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

fun getSortLabel(order: SortOrder): String {
    return when (order) {
        SortOrder.DATE_DESC -> "追加日時: 降順"
        SortOrder.DATE_ASC -> "追加日時: 昇順"
        SortOrder.TEXT_ASC -> "文字コード: 昇順"
        SortOrder.TEXT_DESC -> "文字コード: 降順"
    }
}