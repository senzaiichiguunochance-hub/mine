package com.example.clipnote

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    canAuthenticate: () -> Boolean,
    onRequestAuth: (title: String, onSuccess: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    val list by viewModel.displayItems.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val secretCount by viewModel.secretCount.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val isSecretMode by viewModel.isSecretMode.collectAsState()
    val clipboard by viewModel.clipboard.collectAsState()
    val scrollTarget by viewModel.scrollTarget.collectAsState()
    val sharedText by viewModel.sharedText.collectAsState()
    val pendingImportCount by viewModel.pendingImportCount.collectAsState()

    var searchText by rememberSaveable { mutableStateOf("") }

    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var addInitText by rememberSaveable { mutableStateOf("") }
    var addInitSecret by rememberSaveable { mutableStateOf(false) }

    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var editText by rememberSaveable { mutableStateOf("") }
    var editSecret by rememberSaveable { mutableStateOf(false) }

    var showLimitAlert by rememberSaveable { mutableStateOf(false) }
    var showDeleteAllConfirm by rememberSaveable { mutableStateOf(false) }
    var showReplaceConfirm by rememberSaveable { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::exportTo) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::prepareImport) }

    fun startExport() {
        val name = "clipnote_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + ".json"
        if (secretCount == 0) {
            exportLauncher.launch(name)
        } else if (!canAuthenticate()) {
            toast("認証が使用できないため、シークレット項目を含むエクスポートはできません")
        } else {
            onRequestAuth("エクスポートの認証") { exportLauncher.launch(name) }
        }
    }

    fun tryDelete(item: ClipItem): Boolean {
        if (item.isSecret && !isSecretMode) {
            toast("シークレットモードがOFFのため削除できません")
            return false
        }
        viewModel.deleteItem(item.id)
        return true
    }

    fun tryEdit(item: ClipItem): Boolean {
        if (item.isSecret && !isSecretMode) {
            toast("シークレットモードがOFFのため編集できません")
            return false
        }
        editText = item.text
        editSecret = item.isSecret
        editId = item.id
        return true
    }

    fun copyItem(item: ClipItem) {
        if (item.isSecret && !isSecretMode) {
            toast("シークレットモードがOFFのためコピーできません")
            return
        }
        val clip = ClipData.newPlainText("ClipNote", item.text)
        if (item.isSecret) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        clipboardManager.setPrimaryClip(clip)
        viewModel.updateClipboard(item.text, item.isSecret)
        toast("コピーしました")
    }

    fun openAddDialog(text: String, secret: Boolean) {
        if (totalCount >= MAX_ITEMS) {
            showLimitAlert = true
            return
        }
        addInitText = text
        addInitSecret = secret
        showAddDialog = true
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                when (event) {
                    is UiEvent.Message -> snackbarHostState.showSnackbar(event.text)
                    is UiEvent.Undo -> {
                        val result = snackbarHostState.showSnackbar(
                            message = event.text,
                            actionLabel = "元に戻す",
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.restore(event.entities)
                    }
                }
            }
        }
    }

    LaunchedEffect(scrollTarget) {
        val id = scrollTarget ?: return@LaunchedEffect
        val index = withTimeoutOrNull(2000) {
            snapshotFlow { list.indexOfFirst { it.id == id } }.first { it >= 0 }
        }
        if (index != null) listState.animateScrollToItem(index)
        viewModel.clearScrollTarget()
    }

    LaunchedEffect(sharedText) {
        sharedText?.let {
            viewModel.clearSharedText()
            openAddDialog(it, false)
        }
    }

    LaunchedEffect(isSecretMode) {
        if (!isSecretMode && editSecret) editId = null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { openAddDialog("", false) },
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
            TopPanel(
                clipboard = clipboard,
                searchText = searchText,
                onSearchChange = {
                    searchText = it
                    viewModel.setQuery(it)
                },
                showSearchNote = searchText.isNotBlank() && !isSecretMode && secretCount > 0,
                sortOrder = sortOrder,
                onSortChange = viewModel::setSortOrder,
                totalCount = totalCount,
                isSecretMode = isSecretMode,
                onSecretModeChange = { checked ->
                    when {
                        !checked -> viewModel.setSecretMode(false)
                        !canAuthenticate() ->
                            toast("画面ロックまたは生体認証が未設定のため、シークレットモードを使用できません")
                        else -> onRequestAuth("シークレットモード認証") { viewModel.setSecretMode(true) }
                    }
                },
                onAddFromClipboard = { openAddDialog(clipboard.text, clipboard.sensitive) },
                onDeleteAll = { showDeleteAllConfirm = true },
                onExport = ::startExport,
                onImport = { importLauncher.launch(arrayOf("*/*")) }
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                if (list.isEmpty()) {
                    item {
                        Text(
                            text = if (searchText.isBlank()) "項目がありません" else "該当する項目がありません",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(list, key = { it.id }) { item ->
                    ClipRow(
                        item = item,
                        isSecretMode = isSecretMode,
                        onDelete = { tryDelete(item) },
                        onEdit = { tryEdit(item) },
                        onCopy = { copyItem(item) },
                        onTogglePin = { viewModel.togglePin(item) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    if (showAddDialog) {
        key(addInitText, addInitSecret) {
            TextInputDialog(
                title = "テキストの追加",
                initialText = addInitText,
                initialIsSecret = addInitSecret,
                onConfirm = { text, secret ->
                    viewModel.addItem(text, secret) { result ->
                        when (result) {
                            SaveResult.SAVED -> showAddDialog = false
                            SaveResult.DUPLICATE -> toast("既にあります")
                            SaveResult.LIMIT -> {
                                showAddDialog = false
                                showLimitAlert = true
                            }
                        }
                    }
                },
                onDismiss = { showAddDialog = false }
            )
        }
    }

    editId?.let { id ->
        TextInputDialog(
            title = "テキストの編集",
            initialText = editText,
            initialIsSecret = editSecret,
            onConfirm = { text, secret ->
                viewModel.updateItem(id, text, secret, editText) { result ->
                    if (result == SaveResult.SAVED) editId = null else toast("既にあります")
                }
            },
            onDismiss = { editId = null }
        )
    }

    if (showLimitAlert) {
        AlertDialog(
            onDismissRequest = { showLimitAlert = false },
            title = { Text("上限に達しました") },
            text = { Text("上限（10,000件）に達しました。不要な項目を削除してください。") },
            confirmButton = { TextButton(onClick = { showLimitAlert = false }) { Text("OK") } }
        )
    }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text("確認") },
            text = { Text("リストを全削除しますか？（シークレット項目も含む）") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAllConfirm = false
                    viewModel.deleteAll()
                }) { Text("はい") }
            },
            dismissButton = { TextButton(onClick = { showDeleteAllConfirm = false }) { Text("いいえ") } }
        )
    }

    pendingImportCount?.let { count ->
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            title = { Text("インポート") },
            text = {
                Text(
                    "ファイル内: ${count}件\n\n" +
                        "追加: 重複を除いて現在の内容に追加します\n" +
                        "置き換え: 現在の内容をすべて削除し、ファイルの内容にします"
                )
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { showReplaceConfirm = true }) { Text("置き換え") }
                    TextButton(onClick = viewModel::importAdd) { Text("追加") }
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelImport) { Text("キャンセル") } }
        )
    }

    if (showReplaceConfirm) {
        AlertDialog(
            onDismissRequest = { showReplaceConfirm = false },
            title = { Text("確認") },
            text = { Text("現在の ${totalCount} 件をすべて削除し、ファイルの内容に置き換えます。この操作は元に戻せません。") },
            confirmButton = {
                TextButton(onClick = {
                    showReplaceConfirm = false
                    viewModel.importReplace()
                }) { Text("はい") }
            },
            dismissButton = { TextButton(onClick = { showReplaceConfirm = false }) { Text("いいえ") } }
        )
    }
}

@Composable
private fun TopPanel(
    clipboard: ClipboardState,
    searchText: String,
    onSearchChange: (String) -> Unit,
    showSearchNote: Boolean,
    sortOrder: SortOrder,
    onSortChange: (SortOrder) -> Unit,
    totalCount: Int,
    isSecretMode: Boolean,
    onSecretModeChange: (Boolean) -> Unit,
    onAddFromClipboard: () -> Unit,
    onDeleteAll: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "クリップボード: " + if (clipboard.sensitive) MASK_TEXT else clipboard.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            OutlinedButton(
                onClick = onAddFromClipboard,
                enabled = clipboard.text.isNotBlank()
            ) {
                Text("クリップボードから追加")
            }

            OutlinedTextField(
                value = searchText,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("検索") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (searchText.isNotEmpty()) {
                    {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "クリア")
                        }
                    }
                } else null,
                supportingText = if (showSearchNote) {
                    { Text("シークレットモードがOFFのため、シークレット項目は検索対象外です") }
                } else null
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                var sortExpanded by remember { mutableStateOf(false) }
                Box {
                    Button(
                        onClick = { sortExpanded = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text(text = getSortLabel(sortOrder))
                    }
                    DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(getSortLabel(order)) },
                                onClick = {
                                    onSortChange(order)
                                    sortExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "$totalCount / $MAX_ITEMS")
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "メニュー")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("リストを全削除") },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteAll()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("エクスポート（JSON）") },
                                onClick = {
                                    menuExpanded = false
                                    onExport()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("インポート") },
                                onClick = {
                                    menuExpanded = false
                                    onImport()
                                }
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "シークレット",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Checkbox(checked = isSecretMode, onCheckedChange = onSecretModeChange)
                Text(text = "シークレットモード", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ClipRow(
    item: ClipItem,
    isSecretMode: Boolean,
    onDelete: () -> Boolean,
    onEdit: () -> Boolean,
    onCopy: () -> Unit,
    onTogglePin: () -> Unit
) {
    val masked = item.isSecret && !isSecretMode
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val currentOnDelete by rememberUpdatedState(onDelete)
    val currentOnEdit by rememberUpdatedState(onEdit)

    // falseを返すと行は元の位置に戻る。編集は実行後に常にfalseで戻す
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> currentOnDelete()
                SwipeToDismissBoxValue.StartToEnd -> {
                    currentOnEdit()
                    false
                }
                SwipeToDismissBoxValue.Settled -> true
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                SwipeToDismissBoxValue.Settled -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                }
            ) {
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Icon(Icons.Default.Edit, contentDescription = null)
                    SwipeToDismissBoxValue.EndToStart -> Icon(Icons.Default.Delete, contentDescription = null)
                    SwipeToDismissBoxValue.Settled -> Unit
                }
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .combinedClickable(onClick = { expanded = !expanded }, onLongClick = onCopy)
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onDelete() }) {
                Icon(Icons.Default.Close, contentDescription = "削除", tint = MaterialTheme.colorScheme.outline)
            }

            Column(modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)) {
                Text(
                    text = if (masked) MASK_TEXT else item.text,
                    maxLines = if (expanded && !masked) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    color = if (masked) MaterialTheme.colorScheme.outline else Color.Unspecified
                )
                if (!masked) {
                    val chars = item.text.codePointCount(0, item.text.length)
                    val lines = item.text.lines().size
                    Text(
                        text = "${chars}文字・${lines}行",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onTogglePin) {
                if (item.isPinned) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "ピン留め解除",
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        Icons.Outlined.PushPin,
                        contentDescription = "ピン留め",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            IconButton(onClick = { onEdit() }) {
                Icon(Icons.Default.Edit, contentDescription = "編集", tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    initialText: String,
    initialIsSecret: Boolean,
    onConfirm: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var textState by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(text = initialText, selection = TextRange(initialText.length)))
    }
    var isSecret by rememberSaveable { mutableStateOf(initialIsSecret) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isSecret, onCheckedChange = { isSecret = it })
                    Text(text = "シークレット（秘密の文字）として登録", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(textState.text, isSecret) },
                enabled = textState.text.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}

private fun getSortLabel(order: SortOrder): String = when (order) {
    SortOrder.DATE_DESC -> "追加日時: 降順"
    SortOrder.DATE_ASC -> "追加日時: 昇順"
    SortOrder.TEXT_ASC -> "文字コード: 昇順"
    SortOrder.TEXT_DESC -> "文字コード: 降順"
}
