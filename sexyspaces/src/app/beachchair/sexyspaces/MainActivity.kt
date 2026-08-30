package app.beachchair.sexyspaces

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import java.io.File
import java.util.Locale

private const val DEFAULT_PAGE_TITLE = "Spaces"
private const val LOCK_DELAY_IMMEDIATE = 0L
private const val LOCK_DELAY_QUICK = 1_250L
private const val LOCK_DELAY_RELAXED = 15_000L
private const val TRUSTED_LAUNCH_SETTLE_MS = 2_500L
private const val ORDER_SEPARATOR = "\n"
private const val FIELD_SEPARATOR = "\t"
private const val DEFAULT_GRID_COLUMNS = 3
private const val DEFAULT_TILE_SHAPE = "soft"
private const val DEFAULT_LABEL_TEXT_COLOR = "blush"
private const val DEFAULT_TILE_SIZE_DP = 92
private const val MIN_TILE_SIZE_DP = 72
private const val MAX_TILE_SIZE_DP = 128
private const val REQUEST_PICK_FOLDER_ICON = 4201

private val LABEL_TEXT_COLOR_OPTIONS = listOf(
    LabelTextColorOption("blush", "Blush", Color(0xFFFFC3D9)),
    LabelTextColorOption("ivory", "Ivory", Color(0xFFF7EAF2)),
    LabelTextColorOption("gold", "Gold", Color(0xFFFFD38A)),
    LabelTextColorOption("mint", "Mint", Color(0xFF91EFE3)),
    LabelTextColorOption("lilac", "Lilac", Color(0xFFD8C4FF)),
)

private val SLUT_MODE_MOOD_LINES = listOf(
    "Trouble looks good on you.",
    "Pick your poison.",
    "The room is yours.",
    "No one needs to know.",
    "Indulge.",
    "After Dark is open.",
    "Behave badly.",
)

private val SLUT_MODE_FOLDER_LINES = listOf(
    "Step inside.",
    "Private collection unlocked.",
    "Choose your trouble.",
    "The door is closed.",
)

class MainActivity : FragmentActivity() {
    private val lockHandler = Handler(Looper.getMainLooper())
    private var suppressAutoLockUntilElapsed = 0L
    private val delayedLock = Runnable {
        unlocked = false
        showPicker = false
        showSettings = false
    }

    private var unlocked by mutableStateOf(false)
    private var selectedComponents by mutableStateOf<Set<String>>(emptySet())
    private var selectedComponentOrder by mutableStateOf<List<String>>(emptyList())
    private var folders by mutableStateOf<List<PrivateFolder>>(emptyList())
    private var appFolderAssignments by mutableStateOf<Map<String, String>>(emptyMap())
    private var allApps by mutableStateOf<List<LaunchableApp>>(emptyList())
    private var showPicker by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
    private var openFolderId by mutableStateOf<String?>(null)
    private var moveToFolderApp by mutableStateOf<LaunchableApp?>(null)
    private var pendingFolderIconCrop by mutableStateOf<PendingFolderIconCrop?>(null)
    private var pendingFolderIconPickerId: String? = null
    private var allowFaceUnlock by mutableStateOf(true)
    private var hideInBeachChair by mutableStateOf(true)
    private var lockDelayMs by mutableStateOf(LOCK_DELAY_QUICK)
    private var pageTitle by mutableStateOf(DEFAULT_PAGE_TITLE)
    private var searchKeyword by mutableStateOf("spaces")
    private var slutModeEnabled by mutableStateOf(false)
    private var gridColumnCount by mutableStateOf(DEFAULT_GRID_COLUMNS)
    private var tileShape by mutableStateOf(DEFAULT_TILE_SHAPE)
    private var labelTextColor by mutableStateOf(DEFAULT_LABEL_TEXT_COLOR)
    private var tileSizeDp by mutableStateOf(DEFAULT_TILE_SIZE_DP)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        unlocked = consumeTrustedLaunch()

        lifecycleScope.launch {
            val selected = loadSelectedComponents()
            selectedComponents = selected
            selectedComponentOrder = loadSelectedComponentOrder(selected)
            folders = loadFolders()
            appFolderAssignments = loadAppFolderAssignments(selected)
            allowFaceUnlock = loadAllowFaceUnlock()
            hideInBeachChair = loadHideInBeachChair()
            lockDelayMs = loadLockDelayMs()
            pageTitle = loadPageTitle()
            searchKeyword = loadSearchKeyword()
            slutModeEnabled = loadSlutModeEnabled()
            gridColumnCount = loadGridColumnCount()
            tileShape = loadTileShape()
            labelTextColor = loadLabelTextColor()
            tileSizeDp = loadTileSizeDp()
            allApps = loadLaunchableApps()
        }

        setContent {
            SexySpacesTheme(slutModeEnabled = slutModeEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(roomBackground(slutModeEnabled)),
                ) {
                    when {
                        !unlocked -> LockedScreen(
                            canAuthenticate = canAuthenticate(),
                            slutModeEnabled = slutModeEnabled,
                            onUnlock = ::authenticate,
                        )
                        showPicker -> AppPickerScreen(
                            apps = allApps,
                            selectedComponents = selectedComponents,
                            onBack = { showPicker = false },
                            onToggleApp = ::toggleApp,
                        )
                        showSettings -> SettingsScreen(
                            allowFaceUnlock = allowFaceUnlock,
                            hideInBeachChair = hideInBeachChair,
                            hiddenAppCount = selectedComponents.size,
                            pageTitle = pageTitle,
                            searchKeyword = searchKeyword,
                            slutModeEnabled = slutModeEnabled,
                            gridColumnCount = gridColumnCount,
                            tileShape = tileShape,
                            labelTextColor = labelTextColor,
                            tileSizeDp = tileSizeDp,
                            onAllowFaceUnlockChanged = ::updateAllowFaceUnlock,
                            onHideInBeachChairChanged = ::updateHideInBeachChair,
                            lockDelayMs = lockDelayMs,
                            onLockDelayChanged = ::updateLockDelay,
                            onPageTitleChanged = ::updatePageTitle,
                            onSearchKeywordChanged = ::updateSearchKeyword,
                            onSlutModeChanged = ::updateSlutModeEnabled,
                            onGridColumnCountChanged = ::updateGridColumnCount,
                            onTileShapeChanged = ::updateTileShape,
                            onLabelTextColorChanged = ::updateLabelTextColor,
                            onTileSizeChanged = ::updateTileSizeDp,
                            onBack = { showSettings = false },
                        )
                        pendingFolderIconCrop != null -> FolderIconCropScreen(
                            sourceUri = pendingFolderIconCrop!!.sourceUri,
                            tileShape = tileShape,
                            onCancel = { pendingFolderIconCrop = null },
                            onSave = { zoom, horizontalOffset, verticalOffset ->
                                saveFolderIconCrop(
                                    folderId = pendingFolderIconCrop!!.folderId,
                                    sourceUri = pendingFolderIconCrop!!.sourceUri,
                                    zoom = zoom,
                                    horizontalOffset = horizontalOffset,
                                    verticalOffset = verticalOffset,
                                )
                                pendingFolderIconCrop = null
                            },
                        )
                        moveToFolderApp != null -> FolderPickerScreen(
                            app = moveToFolderApp!!,
                            folders = folders,
                            currentFolderId = appFolderAssignments[moveToFolderApp!!.componentKey],
                            onCreateFolder = {
                                createFolder(assignApp = moveToFolderApp)
                                moveToFolderApp = null
                            },
                            onSelectFolder = { folderId ->
                                moveAppToFolder(moveToFolderApp!!, folderId)
                                moveToFolderApp = null
                            },
                            onBack = { moveToFolderApp = null },
                        )
                        openFolderId != null -> {
                            val folder = folders.firstOrNull { it.id == openFolderId }
                            if (folder == null) {
                                openFolderId = null
                            } else {
                                FolderScreen(
                                    folder = folder,
                                    apps = appsInFolder(folder.id),
                                    slutModeEnabled = slutModeEnabled,
                                    gridColumnCount = gridColumnCount,
                                    tileShape = tileShape,
                                    labelTextColor = labelTextColor,
                                    tileSizeDp = tileSizeDp,
                                    onBack = { openFolderId = null },
                                    onRenameFolder = ::renameFolder,
                                    onChooseFolderIcon = ::launchFolderIconPicker,
                                    onLaunchApp = ::launchApp,
                                    onMoveToHome = { moveAppToFolder(it, null) },
                                )
                            }
                        }
                        else -> PrivateSpaceScreen(
                            pageTitle = pageTitle.displayPageTitle(slutModeEnabled),
                            folders = folderTiles(),
                            apps = orderedSelectedApps().filter { it.componentKey !in appFolderAssignments },
                            suggestedApps = allApps.filter { it.componentKey !in selectedComponents }.take(4),
                            slutModeEnabled = slutModeEnabled,
                            gridColumnCount = gridColumnCount,
                            tileShape = tileShape,
                            labelTextColor = labelTextColor,
                            tileSizeDp = tileSizeDp,
                            onCreateFolder = { createFolder() },
                            onOpenFolder = { openFolderId = it.id },
                            onAddApps = { showPicker = true },
                            onOpenSettings = { showSettings = true },
                            onLock = ::lockRoom,
                            onLaunchApp = ::launchApp,
                            onRemoveApp = ::toggleApp,
                            onMoveApp = ::moveSelectedApp,
                            onDropAppInFolder = { app, folderId -> moveAppToFolder(app, folderId) },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (consumeTrustedLaunch()) {
            showPicker = false
            showSettings = false
        }
    }

    @Deprecated("Use a low request code to avoid FragmentActivity ActivityResultRegistry crashes.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK_FOLDER_ICON || resultCode != RESULT_OK) return
        val folderId = pendingFolderIconPickerId ?: return
        val uri = data?.data ?: return
        pendingFolderIconPickerId = null
        startFolderIconCrop(folderId, uri)
    }

    override fun onStart() {
        super.onStart()
        lockHandler.removeCallbacks(delayedLock)
    }

    override fun onStop() {
        super.onStop()
        if (SystemClock.elapsedRealtime() < suppressAutoLockUntilElapsed) return
        lockHandler.postDelayed(delayedLock, lockDelayMs)
    }

    private fun consumeTrustedLaunch(): Boolean {
        if (!UnlockedLaunchGate.consume(this)) return false
        lockHandler.removeCallbacks(delayedLock)
        // CLEAR_TOP/SINGLE_TOP can deliver onNewIntent before a late onStop from the
        // transient gate activity. Suppress that stop so it cannot immediately relock.
        suppressAutoLockUntilElapsed = SystemClock.elapsedRealtime() + TRUSTED_LAUNCH_SETTLE_MS
        unlocked = true
        return true
    }

    private fun authenticate() {
        if (!canAuthenticate()) {
            performRoomHaptic(strong = false)
            unlocked = true
            return
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Spaces")
            .setSubtitle("Private apps stay quiet until you say otherwise.")
            .setAllowedAuthenticators(authenticators())
            .build()

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    performRoomHaptic(strong = false)
                    unlocked = true
                }
            },
        )
        prompt.authenticate(promptInfo)
    }

    private fun canAuthenticate(): Boolean {
        return BiometricManager.from(this).canAuthenticate(authenticators()) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun authenticators(): Int {
        val biometric = if (allowFaceUnlock) {
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        }
        return biometric or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }

    private fun launchApp(app: LaunchableApp) {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(app.componentName),
        )
    }

    private fun lockRoom() {
        performRoomHaptic(strong = true)
        unlocked = false
    }

    private fun performRoomHaptic(strong: Boolean) {
        val feedback = if (strong) {
            HapticFeedbackConstants.LONG_PRESS
        } else {
            HapticFeedbackConstants.CONFIRM
        }
        window.decorView.performHapticFeedback(feedback)
    }

    private fun toggleApp(app: LaunchableApp) {
        val next = selectedComponents.toMutableSet()
        if (!next.add(app.componentKey)) {
            next.remove(app.componentKey)
        }
        selectedComponents = next
        selectedComponentOrder = if (app.componentKey in next) {
            selectedComponentOrder.withKnownComponents(next) + listOf(app.componentKey).filterNot(selectedComponentOrder::contains)
        } else {
            selectedComponentOrder.filterNot { it == app.componentKey }
        }
        appFolderAssignments = appFolderAssignments.filterKeys { it in next }
        lifecycleScope.launch {
            saveSelectedApps(next, selectedComponentOrder)
            saveAppFolderAssignments(appFolderAssignments)
            notifyPrivateAppsChanged()
        }
    }

    private fun moveSelectedApp(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val orderedApps = orderedSelectedApps()
        val rootApps = orderedApps.filter { it.componentKey !in appFolderAssignments }
        if (fromIndex !in rootApps.indices || toIndex !in rootApps.indices) return

        val nextRootOrder = rootApps.map { it.componentKey }.toMutableList()
        val moved = nextRootOrder.removeAt(fromIndex)
        nextRootOrder.add(toIndex, moved)

        val rootIterator = nextRootOrder.iterator()
        val nextOrder = orderedApps.map { it.componentKey }.map { component ->
            if (component in appFolderAssignments) component else rootIterator.next()
        }
        selectedComponentOrder = nextOrder
        lifecycleScope.launch {
            saveSelectedApps(selectedComponents, nextOrder)
        }
    }

    private fun createFolder(assignApp: LaunchableApp? = null) {
        val folder = PrivateFolder(
            id = System.currentTimeMillis().toString(),
            name = nextFolderName(slutModeEnabled, folders),
        )
        folders = folders + folder
        if (assignApp != null) {
            appFolderAssignments = appFolderAssignments + (assignApp.componentKey to folder.id)
        }
        lifecycleScope.launch {
            saveFolders(folders)
            saveAppFolderAssignments(appFolderAssignments)
        }
    }

    private fun renameFolder(folderId: String, name: String) {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) return
        val nextFolders = folders.map { folder ->
            if (folder.id == folderId) folder.copy(name = normalizedName) else folder
        }
        if (nextFolders == folders) return

        folders = nextFolders
        lifecycleScope.launch {
            saveFolders(nextFolders)
        }
    }

    private fun launchFolderIconPicker(folderId: String) {
        pendingFolderIconPickerId = folderId
        val intent = Intent(Intent.ACTION_GET_CONTENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/*")
        startActivityForResult(Intent.createChooser(intent, "Choose folder photo"), REQUEST_PICK_FOLDER_ICON)
    }

    private fun startFolderIconCrop(folderId: String, sourceUri: Uri) {
        pendingFolderIconCrop = PendingFolderIconCrop(folderId = folderId, sourceUri = sourceUri)
    }

    private fun saveFolderIconCrop(
        folderId: String,
        sourceUri: Uri,
        zoom: Float,
        horizontalOffset: Float,
        verticalOffset: Float,
    ) {
        val nextIconUri = copyCroppedFolderIcon(
            folderId = folderId,
            sourceUri = sourceUri,
            zoom = zoom,
            horizontalOffset = horizontalOffset,
            verticalOffset = verticalOffset,
        ) ?: return
        val nextFolders = folders.map { folder ->
            if (folder.id == folderId) folder.copy(iconUri = nextIconUri) else folder
        }
        if (nextFolders == folders) return

        folders = nextFolders
        lifecycleScope.launch {
            saveFolders(nextFolders)
        }
    }

    private fun copyCroppedFolderIcon(
        folderId: String,
        sourceUri: Uri,
        zoom: Float,
        horizontalOffset: Float,
        verticalOffset: Float,
    ): String? {
        return runCatching {
            val sourceBitmap = contentResolver.openInputStream(sourceUri)?.use(BitmapFactory::decodeStream) ?: return null
            val safeZoom = zoom.coerceIn(1f, 3f)
            val cropSize = (minOf(sourceBitmap.width, sourceBitmap.height) / safeZoom)
                .toInt()
                .coerceAtLeast(1)
            val maxLeft = (sourceBitmap.width - cropSize).coerceAtLeast(0)
            val maxTop = (sourceBitmap.height - cropSize).coerceAtLeast(0)
            val left = (maxLeft * ((horizontalOffset.coerceIn(-1f, 1f) + 1f) / 2f))
                .toInt()
                .coerceIn(0, maxLeft)
            val top = (maxTop * ((verticalOffset.coerceIn(-1f, 1f) + 1f) / 2f))
                .toInt()
                .coerceIn(0, maxTop)
            val croppedBitmap = Bitmap.createBitmap(sourceBitmap, left, top, cropSize, cropSize)
            val iconDir = File(filesDir, "folder-icons").apply { mkdirs() }
            val iconFile = File(iconDir, "${folderId.toFileSafeName()}.png")
            iconFile.outputStream().use { output ->
                croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            if (croppedBitmap != sourceBitmap) croppedBitmap.recycle()
            sourceBitmap.recycle()
            Uri.fromFile(iconFile).toString()
        }.getOrNull()
    }

    private fun moveAppToFolder(app: LaunchableApp, folderId: String?) {
        appFolderAssignments = if (folderId == null) {
            appFolderAssignments - app.componentKey
        } else {
            appFolderAssignments + (app.componentKey to folderId)
        }
        lifecycleScope.launch {
            saveAppFolderAssignments(appFolderAssignments)
        }
    }

    private suspend fun saveFolders(nextFolders: List<PrivateFolder>) {
        privateSpaceDataStore.edit { preferences ->
            preferences[foldersKey] = nextFolders.joinToString(ORDER_SEPARATOR) { folder ->
                listOf(
                    Uri.encode(folder.id),
                    Uri.encode(folder.name),
                    Uri.encode(folder.iconUri.orEmpty()),
                ).joinToString(FIELD_SEPARATOR)
            }
        }
    }

    private suspend fun saveAppFolderAssignments(assignments: Map<String, String>) {
        val folderIds = folders.map { it.id }.toSet()
        privateSpaceDataStore.edit { preferences ->
            preferences[appFolderAssignmentsKey] = assignments
                .filter { (component, folderId) -> component in selectedComponents && folderId in folderIds }
                .entries
                .joinToString(ORDER_SEPARATOR) { (component, folderId) ->
                    "${Uri.encode(component)}$FIELD_SEPARATOR${Uri.encode(folderId)}"
                }
        }
    }

    private suspend fun saveSelectedApps(
        selected: Set<String>,
        order: List<String>,
    ) {
        privateSpaceDataStore.edit { preferences ->
            preferences[selectedAppsKey] = selected
            preferences[selectedAppOrderKey] = order.withKnownComponents(selected).joinToString(ORDER_SEPARATOR)
        }
    }

    private fun updateAllowFaceUnlock(enabled: Boolean) {
        allowFaceUnlock = enabled
        lifecycleScope.launch {
            privateSpaceDataStore.edit { it[allowFaceUnlockKey] = enabled }
        }
    }

    private fun updateHideInBeachChair(enabled: Boolean) {
        hideInBeachChair = enabled
        lifecycleScope.launch {
            privateSpaceDataStore.edit { it[hideInBeachChairKey] = enabled }
            notifyPrivateAppsChanged()
        }
    }

    private fun updateLockDelay(delayMs: Long) {
        lockDelayMs = delayMs
        lifecycleScope.launch {
            privateSpaceDataStore.edit { it[lockDelayMsKey] = delayMs.toString() }
        }
    }

    private fun updatePageTitle(title: String) {
        pageTitle = title
        lifecycleScope.launch {
            privateSpaceDataStore.edit { preferences ->
                val normalizedTitle = title.trim()
                if (normalizedTitle.isEmpty() || normalizedTitle == DEFAULT_PAGE_TITLE) {
                    preferences.remove(pageTitleKey)
                } else {
                    preferences[pageTitleKey] = normalizedTitle
                }
            }
        }
    }

    private fun updateSearchKeyword(keyword: String) {
        val normalized = keyword.toSearchKeyword()
        searchKeyword = normalized
        lifecycleScope.launch {
            privateSpaceDataStore.edit { preferences ->
                if (normalized == "spaces") {
                    preferences.remove(searchKeywordKey)
                } else {
                    preferences[searchKeywordKey] = normalized
                }
            }
            notifyPrivateAppsChanged()
        }
    }

    private fun updateSlutModeEnabled(enabled: Boolean) {
        slutModeEnabled = enabled
        performRoomHaptic(strong = enabled)
        lifecycleScope.launch {
            privateSpaceDataStore.edit { it[slutModeEnabledKey] = enabled }
        }
    }

    private fun updateGridColumnCount(columns: Int) {
        gridColumnCount = columns.coerceIn(2, 5)
        lifecycleScope.launch {
            privateSpaceDataStore.edit { it[gridColumnCountKey] = gridColumnCount.toString() }
        }
    }

    private fun updateTileShape(shape: String) {
        tileShape = shape.toTileShape()
        lifecycleScope.launch {
            privateSpaceDataStore.edit { it[tileShapeKey] = tileShape }
        }
    }

    private fun updateLabelTextColor(colorKey: String) {
        labelTextColor = colorKey.toLabelTextColor()
        lifecycleScope.launch {
            privateSpaceDataStore.edit { it[labelTextColorKey] = labelTextColor }
        }
    }

    private fun updateTileSizeDp(sizeDp: Int) {
        tileSizeDp = sizeDp.coerceIn(MIN_TILE_SIZE_DP, MAX_TILE_SIZE_DP)
        lifecycleScope.launch {
            privateSpaceDataStore.edit { it[tileSizeDpKey] = tileSizeDp.toString() }
        }
    }

    private fun notifyPrivateAppsChanged() {
        contentResolver.notifyChange(PRIVATE_APPS_URI, null)
    }

    private suspend fun loadSelectedComponents(): Set<String> {
        return privateSpaceDataStore.data.first()[selectedAppsKey].orEmpty()
    }

    private suspend fun loadSelectedComponentOrder(selected: Set<String>): List<String> {
        val storedOrder = privateSpaceDataStore.data.first()[selectedAppOrderKey]
            .orEmpty()
            .split(ORDER_SEPARATOR)
            .filter { it.isNotBlank() }
        return storedOrder.withKnownComponents(selected) + selected
            .filterNot(storedOrder::contains)
            .sorted()
    }

    private suspend fun loadFolders(): List<PrivateFolder> {
        return privateSpaceDataStore.data.first()[foldersKey]
            .orEmpty()
            .split(ORDER_SEPARATOR)
            .mapNotNull { line ->
                val parts = line.split(FIELD_SEPARATOR, limit = 3)
                if (parts.size < 2) return@mapNotNull null
                PrivateFolder(
                    id = Uri.decode(parts[0]),
                    name = Uri.decode(parts[1]).ifBlank { "Folder" },
                    iconUri = parts.getOrNull(2)?.let(Uri::decode)?.ifBlank { null },
                )
            }
            .distinctBy { it.id }
    }

    private suspend fun loadAppFolderAssignments(selected: Set<String>): Map<String, String> {
        val folderIds = folders.map { it.id }.toSet()
        return privateSpaceDataStore.data.first()[appFolderAssignmentsKey]
            .orEmpty()
            .split(ORDER_SEPARATOR)
            .mapNotNull { line ->
                val parts = line.split(FIELD_SEPARATOR, limit = 2)
                if (parts.size != 2) return@mapNotNull null
                Uri.decode(parts[0]) to Uri.decode(parts[1])
            }
            .filter { (component, folderId) -> component in selected && folderId in folderIds }
            .toMap()
    }

    private suspend fun loadAllowFaceUnlock(): Boolean {
        return privateSpaceDataStore.data.first()[allowFaceUnlockKey] ?: true
    }

    private suspend fun loadHideInBeachChair(): Boolean {
        return privateSpaceDataStore.data.first()[hideInBeachChairKey] ?: true
    }

    private suspend fun loadLockDelayMs(): Long {
        val stored = privateSpaceDataStore.data.first()[lockDelayMsKey]?.toLongOrNull()
        return when (stored) {
            LOCK_DELAY_IMMEDIATE,
            LOCK_DELAY_QUICK,
            LOCK_DELAY_RELAXED,
            -> stored
            else -> LOCK_DELAY_QUICK
        }
    }

    private suspend fun loadPageTitle(): String {
        return privateSpaceDataStore.data.first()[pageTitleKey] ?: DEFAULT_PAGE_TITLE
    }

    private suspend fun loadSearchKeyword(): String {
        return privateSpaceDataStore.data.first()[searchKeywordKey].orEmpty().toSearchKeyword()
    }

    private suspend fun loadSlutModeEnabled(): Boolean {
        return privateSpaceDataStore.data.first()[slutModeEnabledKey] ?: false
    }

    private suspend fun loadGridColumnCount(): Int {
        return privateSpaceDataStore.data.first()[gridColumnCountKey]
            ?.toIntOrNull()
            ?.coerceIn(2, 5)
            ?: DEFAULT_GRID_COLUMNS
    }

    private suspend fun loadTileShape(): String {
        return privateSpaceDataStore.data.first()[tileShapeKey].orEmpty().toTileShape()
    }

    private suspend fun loadLabelTextColor(): String {
        return privateSpaceDataStore.data.first()[labelTextColorKey].orEmpty().toLabelTextColor()
    }

    private suspend fun loadTileSizeDp(): Int {
        return privateSpaceDataStore.data.first()[tileSizeDpKey]
            ?.toIntOrNull()
            ?.coerceIn(MIN_TILE_SIZE_DP, MAX_TILE_SIZE_DP)
            ?: DEFAULT_TILE_SIZE_DP
    }

    private fun loadLaunchableApps(): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                val component = ComponentName(activityInfo.packageName, activityInfo.name)
                LaunchableApp(
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = activityInfo.packageName,
                    componentName = component,
                    icon = resolveInfo.loadIcon(packageManager),
                )
            }
            .filterNot { it.packageName == packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun orderedSelectedApps(): List<LaunchableApp> {
        val appsByKey = allApps.associateBy { it.componentKey }
        return (selectedComponentOrder.withKnownComponents(selectedComponents) + selectedComponents.filterNot(selectedComponentOrder::contains))
            .mapNotNull(appsByKey::get)
    }

    private fun appsInFolder(folderId: String): List<LaunchableApp> {
        val appKeys = appFolderAssignments
            .filterValues { it == folderId }
            .keys
        return orderedSelectedApps().filter { it.componentKey in appKeys }
    }

    private fun folderTiles(): List<PrivateFolderTile> {
        return folders.map { folder ->
            PrivateFolderTile(
                folder = folder,
                appCount = appFolderAssignments.count { it.value == folder.id },
            )
        }
    }

}

private fun String.displayPageTitle(slutModeEnabled: Boolean): String {
    val title = trim()
    return when {
        title.isNotEmpty() && title != DEFAULT_PAGE_TITLE -> title
        slutModeEnabled -> "Sexy Spaces"
        else -> DEFAULT_PAGE_TITLE
    }
}

private fun List<String>.withKnownComponents(selected: Set<String>): List<String> {
    return filter { it in selected }.distinct()
}

private data class PrivateFolder(
    val id: String,
    val name: String,
    val iconUri: String? = null,
)

private data class PrivateFolderTile(
    val folder: PrivateFolder,
    val appCount: Int,
)

private data class PendingFolderIconCrop(
    val folderId: String,
    val sourceUri: Uri,
)

private data class DraggedApp(
    val app: LaunchableApp,
    val fromIndex: Int,
    val pointerPosition: Offset,
)

private data class LabelTextColorOption(
    val key: String,
    val label: String,
    val color: Color,
)

private fun nextFolderName(
    slutModeEnabled: Boolean,
    folders: List<PrivateFolder>,
): String {
    if (!slutModeEnabled) return "Folder ${folders.size + 1}"

    val names = listOf("After Dark", "Top Shelf", "Trouble", "Toys", "Clips", "Domme", "Favorites")
    val usedNames = folders.map { it.name }.toSet()
    return names.firstOrNull { it !in usedNames } ?: "After Dark ${folders.size + 1}"
}

private fun roomBackground(slutModeEnabled: Boolean): Brush {
    return Brush.verticalGradient(
        if (slutModeEnabled) {
            listOf(
                Color(0xFF21030C),
                Color(0xFF12040A),
                Color(0xFF070305),
            )
        } else {
            listOf(
                Color(0xFF0B070D),
                Color(0xFF0B070D),
            )
        },
    )
}

private fun String.toTileShape(): String {
    return when (lowercase(Locale.getDefault())) {
        "sharp", "round", "soft" -> lowercase(Locale.getDefault())
        else -> DEFAULT_TILE_SHAPE
    }
}

private fun tileCornerRadius(tileShape: String, tileSizeDp: Int = DEFAULT_TILE_SIZE_DP): Int {
    val safeSize = tileSizeDp.coerceIn(MIN_TILE_SIZE_DP, MAX_TILE_SIZE_DP)
    return when (tileShape.toTileShape()) {
        "sharp" -> (safeSize * 0.15f).toInt()
        "round" -> (safeSize * 0.46f).toInt()
        else -> (safeSize * 0.30f).toInt()
    }
}

private fun tileOuterCornerRadius(tileShape: String, tileSizeDp: Int = DEFAULT_TILE_SIZE_DP): Int {
    val safeSize = tileSizeDp.coerceIn(MIN_TILE_SIZE_DP, MAX_TILE_SIZE_DP)
    return when (tileShape.toTileShape()) {
        "sharp" -> (safeSize * 0.11f).toInt()
        "round" -> (safeSize * 0.26f).toInt()
        else -> (safeSize * 0.20f).toInt()
    }
}

private fun String.toLabelTextColor(): String {
    val normalized = lowercase(Locale.getDefault())
    return LABEL_TEXT_COLOR_OPTIONS.firstOrNull { it.key == normalized }?.key ?: DEFAULT_LABEL_TEXT_COLOR
}

private fun labelTextColor(colorKey: String): Color {
    return LABEL_TEXT_COLOR_OPTIONS.firstOrNull { it.key == colorKey.toLabelTextColor() }?.color
        ?: LABEL_TEXT_COLOR_OPTIONS.first().color
}

private fun String.toFileSafeName(): String {
    return filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifBlank { "folder" }
}

@Composable
private fun readableLabelStyle(colorKey: String) = MaterialTheme.typography.titleSmall.copy(
    color = labelTextColor(colorKey),
    shadow = Shadow(
        color = Color.Black.copy(alpha = 0.95f),
        offset = Offset(0f, 2f),
        blurRadius = 6f,
    ),
)

private fun String.toSearchKeyword(): String {
    return trim()
        .lowercase(Locale.getDefault())
        .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        .ifEmpty { "spaces" }
}

private data class LaunchableApp(
    val label: String,
    val packageName: String,
    val componentName: ComponentName,
    val icon: Drawable,
) {
    val componentKey: String = componentName.flattenToString()
    val initials: String = label.trim().take(1).ifEmpty { "?" }.uppercase()
}

@Composable
private fun SexySpacesTheme(
    slutModeEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (slutModeEnabled) {
        androidx.compose.material3.darkColorScheme(
            primary = Color(0xFFFF2D73),
            secondary = Color(0xFFFFD38A),
            surface = Color(0xFF190A10),
            surfaceVariant = Color(0xFF32111F),
            background = Color(0xFF070305),
        )
    } else {
        androidx.compose.material3.darkColorScheme(
            primary = Color(0xFFFF6BAA),
            secondary = Color(0xFFFFC3D9),
            surface = Color(0xFF151018),
            surfaceVariant = Color(0xFF241A25),
            background = Color(0xFF0B070D),
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

@Composable
private fun LockedScreen(
    canAuthenticate: Boolean,
    slutModeEnabled: Boolean,
    onUnlock: () -> Unit,
) {
    val titleText = if (slutModeEnabled) "Sexy Spaces" else "Spaces"
    val subtitleText = if (canAuthenticate) {
        if (slutModeEnabled) {
            "Your private room is locked."
        } else {
            "Your private app room is locked."
        }
    } else {
        "Set a device lock to protect this space."
    }
    val backgroundColors = if (slutModeEnabled) {
        listOf(Color(0xFF3D0716), Color(0xFF17030B), Color(0xFF070305))
    } else {
        listOf(Color(0xFF231126), Color(0xFF110B15), Color(0xFF07070A))
    }
    val accentColor = if (slutModeEnabled) Color(0xFFFF6BAA) else Color(0xFFFFC3D9)
    val warmAccentColor = Color(0xFFFFD38A)
    val readableTextShadow = Shadow(
        color = Color.Black.copy(alpha = 0.95f),
        offset = Offset(0f, 3f),
        blurRadius = 10f,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(backgroundColors)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accentColor.copy(alpha = 0.28f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.52f),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                accentColor.copy(alpha = 0.42f),
                                Color(0xFF100811),
                            ),
                        ),
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.24f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(94.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.38f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VisibilityOff,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(46.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(30.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.34f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = titleText,
                    color = Color.White,
                    style = MaterialTheme.typography.displaySmall.copy(shadow = readableTextShadow),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = subtitleText,
                    color = Color(0xFFFFEEF6),
                    style = MaterialTheme.typography.titleMedium.copy(shadow = readableTextShadow),
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Private apps stay quiet until you say otherwise.",
                    color = Color(0xFFE8D5E2),
                    style = MaterialTheme.typography.bodyMedium.copy(shadow = readableTextShadow),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(26.dp))
            Button(
                onClick = onUnlock,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = Color(0xFF130711),
                ),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
            )
            {
                Icon(Icons.Rounded.LockOpen, contentDescription = null)
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = if (canAuthenticate) "Unlock" else "Continue",
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Protected by your device lock",
                color = warmAccentColor,
                style = MaterialTheme.typography.bodySmall.copy(shadow = readableTextShadow),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivateSpaceScreen(
    pageTitle: String,
    folders: List<PrivateFolderTile>,
    apps: List<LaunchableApp>,
    suggestedApps: List<LaunchableApp>,
    slutModeEnabled: Boolean,
    gridColumnCount: Int,
    tileShape: String,
    labelTextColor: String,
    tileSizeDp: Int,
    onCreateFolder: () -> Unit,
    onOpenFolder: (PrivateFolder) -> Unit,
    onAddApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onLock: () -> Unit,
    onLaunchApp: (LaunchableApp) -> Unit,
    onRemoveApp: (LaunchableApp) -> Unit,
    onMoveApp: (fromIndex: Int, toIndex: Int) -> Unit,
    onDropAppInFolder: (LaunchableApp, String) -> Unit,
) {
    var draggedApp by remember { mutableStateOf<DraggedApp?>(null) }
    val appBounds = remember { mutableStateMapOf<String, Rect>() }
    val folderBounds = remember { mutableStateMapOf<String, Rect>() }
    val itemCount = apps.size + folders.size
    val moodLine = remember(slutModeEnabled) { SLUT_MODE_MOOD_LINES.random() }
    val dragTargetFolderId = draggedApp?.pointerPosition?.let { pointerPosition ->
        folderBounds.entries.firstOrNull { (_, bounds) -> bounds.contains(pointerPosition) }?.key
    }

    fun appIndexAt(pointerPosition: Offset): Int? {
        return apps.mapIndexedNotNull { index, app ->
            if (app.componentKey == draggedApp?.app?.componentKey) return@mapIndexedNotNull null
            val bounds = appBounds[app.componentKey] ?: return@mapIndexedNotNull null
            if (bounds.contains(pointerPosition)) index else null
        }.firstOrNull()
    }

    fun finishDrag() {
        val drag = draggedApp ?: return
        val folderTarget = folderBounds.entries.firstOrNull { (_, bounds) ->
            bounds.contains(drag.pointerPosition)
        }?.key
        if (folderTarget != null) {
            onDropAppInFolder(drag.app, folderTarget)
            draggedApp = null
            return
        }
        draggedApp = null
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(pageTitle)
                        if (slutModeEnabled) {
                            Text(
                                text = moodLine,
                                color = Color(0xFFFFD38A),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else if (itemCount > 0) {
                            Text(
                                text = if (slutModeEnabled) "$itemCount private indulgences" else "$itemCount private items",
                                color = Color(0xFFB9A7B5),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (slutModeEnabled && itemCount > 0) {
                            Text(
                                text = "$itemCount private indulgences",
                                color = Color(0xFFB9A7B5),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    IconButton(onClick = onCreateFolder) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = "Create folder")
                    }
                    IconButton(onClick = onAddApps) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add apps")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = onLock) {
                        Icon(Icons.Rounded.Lock, contentDescription = "Lock")
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumnCount),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (itemCount == 0) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptySpace(
                        suggestedApps = suggestedApps,
                        slutModeEnabled = slutModeEnabled,
                        onAddApps = onAddApps,
                        onAddSuggestedApp = onRemoveApp,
                    )
                }
            } else {
                items(folders, key = { "folder:${it.folder.id}" }) { folderTile ->
                    FolderTileView(
                        folderTile = folderTile,
                        tileShape = tileShape,
                        labelTextColor = labelTextColor,
                        tileSizeDp = tileSizeDp,
                        modifier = Modifier.animateItem(),
                        isDropTarget = dragTargetFolderId == folderTile.folder.id,
                        onBoundsChanged = { folderBounds[folderTile.folder.id] = it },
                        onClick = { onOpenFolder(folderTile.folder) },
                    )
                }
                itemsIndexed(apps, key = { _, app -> app.componentKey }) { index, app ->
                    PrivateAppTile(
                        app = app,
                        tileShape = tileShape,
                        labelTextColor = labelTextColor,
                        tileSizeDp = tileSizeDp,
                        modifier = Modifier.animateItem(),
                        isDragging = draggedApp?.app?.componentKey == app.componentKey,
                        onBoundsChanged = { appBounds[app.componentKey] = it },
                        onDragStart = { pointerPosition ->
                            draggedApp = DraggedApp(app, index, pointerPosition)
                        },
                        onDrag = { dragAmount ->
                            draggedApp = draggedApp?.let { drag ->
                                val nextPointerPosition = drag.pointerPosition + dragAmount
                                val targetIndex = appIndexAt(nextPointerPosition)
                                if (targetIndex != null && targetIndex != drag.fromIndex) {
                                    onMoveApp(drag.fromIndex, targetIndex)
                                    drag.copy(fromIndex = targetIndex, pointerPosition = nextPointerPosition)
                                } else {
                                    drag.copy(pointerPosition = nextPointerPosition)
                                }
                            }
                        },
                        onDragEnd = ::finishDrag,
                        onClick = { onLaunchApp(app) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyStatusCard(
    appCount: Int,
    hideInBeachChair: Boolean,
    slutModeEnabled: Boolean,
) {
    val statusText = if (hideInBeachChair) {
        "$appCount apps hidden from your launcher drawer and search"
    } else {
        "$appCount apps visible in your launcher drawer and search"
    }
    val detailText = if (hideInBeachChair) {
        if (slutModeEnabled) {
            "Sexy Spaces keeps your dirty little shortcuts out of the launcher until the room is unlocked."
        } else {
            "Spaces is keeping selected apps out of the launcher until you unlock this room."
        }
    } else {
        "Selected apps are protected here, but still appear in the normal launcher surfaces."
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181219)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (hideInBeachChair) Color(0x26FF6BAA) else Color(0x262EC4B6)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (hideInBeachChair) Icons.Rounded.VisibilityOff else Icons.Rounded.Info,
                    contentDescription = null,
                    tint = if (hideInBeachChair) Color(0xFFFFC3D9) else Color(0xFF91EFE3),
                )
            }
            Spacer(modifier = Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(statusText, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    detailText,
                    color = Color(0xFFB9A7B5),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    allowFaceUnlock: Boolean,
    hideInBeachChair: Boolean,
    hiddenAppCount: Int,
    lockDelayMs: Long,
    pageTitle: String,
    searchKeyword: String,
    slutModeEnabled: Boolean,
    gridColumnCount: Int,
    tileShape: String,
    labelTextColor: String,
    tileSizeDp: Int,
    onAllowFaceUnlockChanged: (Boolean) -> Unit,
    onHideInBeachChairChanged: (Boolean) -> Unit,
    onLockDelayChanged: (Long) -> Unit,
    onPageTitleChanged: (String) -> Unit,
    onSearchKeywordChanged: (String) -> Unit,
    onSlutModeChanged: (Boolean) -> Unit,
    onGridColumnCountChanged: (Int) -> Unit,
    onTileShapeChanged: (String) -> Unit,
    onLabelTextColorChanged: (String) -> Unit,
    onTileSizeChanged: (Int) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = Color(0xFF0B070D),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0B070D)),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Display",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFFC3D9),
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181219)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Page title", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pageTitle,
                        onValueChange = onPageTitleChanged,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Spaces") },
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Leave this blank to use Spaces.",
                        color = Color(0xFFB9A7B5),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181219)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Grid", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Choose how dense the Spaces homepage feels.",
                        color = Color(0xFFB9A7B5),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        GridOptionButton(
                            label = "Cozy",
                            selected = gridColumnCount == 2,
                            onClick = { onGridColumnCountChanged(2) },
                            modifier = Modifier.weight(1f),
                        )
                        GridOptionButton(
                            label = "Roomy",
                            selected = gridColumnCount == 3,
                            onClick = { onGridColumnCountChanged(3) },
                            modifier = Modifier.weight(1f),
                        )
                        GridOptionButton(
                            label = "Gallery",
                            selected = gridColumnCount == 4,
                            onClick = { onGridColumnCountChanged(4) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181219)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tile size", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Resize app and folder tiles.",
                                color = Color(0xFFB9A7B5),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            "$tileSizeDp dp",
                            color = Color(0xFFFFC3D9),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Slider(
                        value = tileSizeDp.toFloat(),
                        onValueChange = { onTileSizeChanged(it.toInt()) },
                        valueRange = MIN_TILE_SIZE_DP.toFloat()..MAX_TILE_SIZE_DP.toFloat(),
                        steps = 6,
                    )
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181219)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Label color", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Choose the color used for app and folder names.",
                        color = Color(0xFFB9A7B5),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LABEL_TEXT_COLOR_OPTIONS.chunked(3).forEach { rowOptions ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                rowOptions.forEach { option ->
                                    LabelColorOptionButton(
                                        option = option,
                                        selected = labelTextColor.toLabelTextColor() == option.key,
                                        onClick = { onLabelTextColorChanged(option.key) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(3 - rowOptions.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181219)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Tile shape", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Tune the room from sharp and glossy to soft and plush.",
                        color = Color(0xFFB9A7B5),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        GridOptionButton(
                            label = "Sharp",
                            selected = tileShape == "sharp",
                            onClick = { onTileShapeChanged("sharp") },
                            modifier = Modifier.weight(1f),
                        )
                        GridOptionButton(
                            label = "Soft",
                            selected = tileShape == "soft",
                            onClick = { onTileShapeChanged("soft") },
                            modifier = Modifier.weight(1f),
                        )
                        GridOptionButton(
                            label = "Round",
                            selected = tileShape == "round",
                            onClick = { onTileShapeChanged("round") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181219)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Search shortcut", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchKeyword,
                        onValueChange = onSearchKeywordChanged,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("spaces") },
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Type this word in your launcher search to open Spaces.",
                        color = Color(0xFFB9A7B5),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181219)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Slut Mode", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Make the unlocked room louder, hotter, and less polite. Spaces stays discreet outside the room.",
                            color = Color(0xFFB9A7B5),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = slutModeEnabled, onCheckedChange = onSlutModeChanged)
                }
            }
            Text(
                "Privacy",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFFC3D9),
            )
            PrivacyStatusCard(
                appCount = hiddenAppCount,
                hideInBeachChair = hideInBeachChair,
                slutModeEnabled = slutModeEnabled,
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181219)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Face unlock", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Allow convenience biometrics when your phone supports them. Turn this off to require stronger biometrics or your device credential.",
                            color = Color(0xFFB9A7B5),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = allowFaceUnlock, onCheckedChange = onAllowFaceUnlockChanged)
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181219)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hide in launcher", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Selected apps disappear from your launcher drawer and search while they live in Spaces.",
                            color = Color(0xFFB9A7B5),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = hideInBeachChair, onCheckedChange = onHideInBeachChairChanged)
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181219)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Lock timing", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Choose how quickly Spaces locks after you leave it.",
                        color = Color(0xFFB9A7B5),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        LockDelayOption(
                            label = "Immediate",
                            selected = lockDelayMs == LOCK_DELAY_IMMEDIATE,
                            onClick = { onLockDelayChanged(LOCK_DELAY_IMMEDIATE) },
                            modifier = Modifier.weight(1f),
                        )
                        LockDelayOption(
                            label = "Quick",
                            selected = lockDelayMs == LOCK_DELAY_QUICK,
                            onClick = { onLockDelayChanged(LOCK_DELAY_QUICK) },
                            modifier = Modifier.weight(1f),
                        )
                        LockDelayOption(
                            label = "Relaxed",
                            selected = lockDelayMs == LOCK_DELAY_RELAXED,
                            onClick = { onLockDelayChanged(LOCK_DELAY_RELAXED) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181219)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("How unlock works", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "When face unlock is enabled, Spaces asks Android for weaker or stronger biometrics plus device credential fallback. If your phone exposes face unlock to apps, it can appear in the system prompt.",
                        color = Color(0xFFB9A7B5),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun LockDelayOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) Color(0xFFFF6BAA) else Color(0xFF241A25),
            contentColor = if (selected) Color(0xFF21101D) else Color(0xFFFFC3D9),
        ),
    ) {
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun GridOptionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) Color(0xFFFF6BAA) else Color(0xFF241A25),
            contentColor = if (selected) Color(0xFF21101D) else Color(0xFFFFC3D9),
        ),
    ) {
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LabelColorOptionButton(
    option: LabelTextColorOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFF392036) else Color(0xFF241A25),
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(option.color)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) Color.White else Color(0x66FFFFFF),
                        shape = CircleShape,
                    ),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = option.label,
                color = option.color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun EmptySpace(
    suggestedApps: List<LaunchableApp>,
    slutModeEnabled: Boolean,
    onAddApps: () -> Unit,
    onAddSuggestedApp: (LaunchableApp) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (slutModeEnabled) "Nothing naughty in here yet" else "Nothing in here yet",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (slutModeEnabled) {
                    "Add the apps that deserve their own private little room."
                } else {
                    "Add private apps here and choose whether your launcher hides them from drawer and search."
                },
                color = Color(0xFFD8C5D4),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onAddApps) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(10.dp))
                Text("Add apps")
            }
            if (suggestedApps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF181219)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Quick picks", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Tap any app to add it now.",
                            color = Color(0xFFB9A7B5),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        suggestedApps.forEach { app ->
                            SuggestedAppRow(
                                app = app,
                                onClick = { onAddSuggestedApp(app) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestedAppRow(
    app: LaunchableApp,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(Color(0xFF241A25))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = rememberDrawablePainter(app.icon),
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            app.label,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(Icons.Rounded.Add, contentDescription = "Add ${app.label}")
    }
}

@Composable
private fun FolderTileView(
    folderTile: PrivateFolderTile,
    tileShape: String,
    labelTextColor: String,
    tileSizeDp: Int,
    modifier: Modifier = Modifier,
    isDropTarget: Boolean = false,
    onBoundsChanged: (Rect) -> Unit = {},
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val folderIcon = remember(folderTile.folder.iconUri) {
        folderTile.folder.iconUri
            ?.let(Uri::parse)
            ?.let { uri ->
                runCatching {
                    if (uri.scheme == "file") {
                        Drawable.createFromPath(uri.path)
                    } else {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            Drawable.createFromStream(input, null)
                        }
                    }
                }.getOrNull()
            }
    }
    Column(
        modifier = modifier
            .onGloballyPositioned { onBoundsChanged(it.boundsInWindow()) }
            .graphicsLayer {
                scaleX = if (isDropTarget) 1.04f else 1f
                scaleY = if (isDropTarget) 1.04f else 1f
                shadowElevation = if (isDropTarget) 10f else 0f
            }
            .clip(RoundedCornerShape(tileOuterCornerRadius(tileShape, tileSizeDp).dp))
            .clickable(onClick = onClick)
            .border(
                width = if (isDropTarget) 2.dp else 0.dp,
                color = if (isDropTarget) Color(0xFFFFD38A) else Color.Transparent,
                shape = RoundedCornerShape(tileOuterCornerRadius(tileShape, tileSizeDp).dp),
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(tileSizeDp.coerceIn(MIN_TILE_SIZE_DP, MAX_TILE_SIZE_DP).dp)
                .clip(RoundedCornerShape(tileCornerRadius(tileShape, tileSizeDp).dp))
                .background(if (isDropTarget) Color(0xFF3A2634) else Color(0xFF2A1D2B))
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (folderIcon != null) {
                Image(
                    painter = rememberDrawablePainter(folderIcon),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape((tileCornerRadius(tileShape, tileSizeDp) - 8).coerceAtLeast(8).dp)),
                )
            } else {
                Icon(
                    Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = Color(0xFFFFC3D9),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = folderTile.folder.name,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x8C000000))
                .padding(horizontal = 7.dp, vertical = 3.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = readableLabelStyle(labelTextColor),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "${folderTile.appCount} apps",
            color = Color(0xFFB9A7B5),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PrivateAppTile(
    app: LaunchableApp,
    tileShape: String,
    labelTextColor: String,
    tileSizeDp: Int,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    onBoundsChanged: (Rect) -> Unit = {},
    onDragStart: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onClick: () -> Unit,
) {
    var tileBounds by remember(app.componentKey) { mutableStateOf<Rect?>(null) }
    var dragOffset by remember(app.componentKey) { mutableStateOf(Offset.Zero) }
    val wiggleTransition = rememberInfiniteTransition(label = "appTileWiggle")
    val wiggleRotation by wiggleTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 82),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "appTileWiggleRotation",
    )
    val wiggleLift by wiggleTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 120),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "appTileWiggleLift",
    )
    Column(
        modifier = modifier
            .onGloballyPositioned {
                val bounds = it.boundsInWindow()
                tileBounds = bounds
                onBoundsChanged(bounds)
            }
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationX = if (isDragging) dragOffset.x else 0f
                translationY = if (isDragging) dragOffset.y + wiggleLift else 0f
                rotationZ = if (isDragging) wiggleRotation else 0f
                shadowElevation = if (isDragging) 24f else 0f
                scaleX = if (isDragging) 1.12f else 1f
                scaleY = if (isDragging) 1.12f else 1f
            }
            .pointerInput(app.componentKey) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { localOffset ->
                        val bounds = tileBounds ?: return@detectDragGesturesAfterLongPress
                        dragOffset = Offset.Zero
                        onDragStart(Offset(bounds.left + localOffset.x, bounds.top + localOffset.y))
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount
                        onDrag(dragAmount)
                    },
                    onDragEnd = {
                        dragOffset = Offset.Zero
                        onDragEnd()
                    },
                    onDragCancel = {
                        dragOffset = Offset.Zero
                        onDragEnd()
                    },
                )
            }
            .clip(RoundedCornerShape(tileOuterCornerRadius(tileShape, tileSizeDp).dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(tileSizeDp.coerceIn(MIN_TILE_SIZE_DP, MAX_TILE_SIZE_DP).dp)
                .clip(RoundedCornerShape(tileCornerRadius(tileShape, tileSizeDp).dp))
                .background(Color(0xFF211722))
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = rememberDrawablePainter(app.icon),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = app.label,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x8C000000))
                .padding(horizontal = 7.dp, vertical = 3.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = readableLabelStyle(labelTextColor),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HomeDropTarget(
    active: Boolean,
    onBoundsChanged: (Rect) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (active) Color(0xFF392036) else Color(0xFF181219),
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onBoundsChanged(it.boundsInWindow()) }
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) Color(0xFFFFD38A) else Color(0x33FFFFFF),
                shape = RoundedCornerShape(18.dp),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (active) Color(0x33FFD38A) else Color(0x26FF6BAA)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Home,
                    contentDescription = null,
                    tint = if (active) Color(0xFFFFD38A) else Color(0xFFFFC3D9),
                )
            }
            Spacer(modifier = Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Main screen", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Return to the Spaces homepage",
                    color = Color(0xFFB9A7B5),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderIconCropScreen(
    sourceUri: Uri,
    tileShape: String,
    onCancel: () -> Unit,
    onSave: (zoom: Float, horizontalOffset: Float, verticalOffset: Float) -> Unit,
) {
    val context = LocalContext.current
    val sourceDrawable = remember(sourceUri) {
        runCatching {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                Drawable.createFromStream(input, null)
            }
        }.getOrNull()
    }
    var zoom by remember(sourceUri) { mutableStateOf(1f) }
    var horizontalOffset by remember(sourceUri) { mutableStateOf(0f) }
    var verticalOffset by remember(sourceUri) { mutableStateOf(0f) }
    val iconShape = RoundedCornerShape(tileCornerRadius(tileShape).dp)

    Scaffold(
        containerColor = Color(0xFF0B070D),
        topBar = {
            TopAppBar(
                title = { Text("Crop folder icon") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        enabled = sourceDrawable != null,
                        onClick = { onSave(zoom, horizontalOffset, verticalOffset) },
                    ) {
                        Text("Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0B070D)),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(iconShape)
                    .background(Color(0xFF211722))
                    .border(2.dp, Color(0xFFFFC3D9), iconShape),
                contentAlignment = Alignment.Center,
            ) {
                if (sourceDrawable != null) {
                    Image(
                        painter = rememberDrawablePainter(sourceDrawable),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = horizontalOffset * 70f
                                translationY = verticalOffset * 70f
                            },
                    )
                } else {
                    Text(
                        "Unable to load image",
                        color = Color(0xFFFFEEF6),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            CropSlider(
                label = "Zoom",
                value = zoom,
                valueRange = 1f..3f,
                onValueChange = { zoom = it },
            )
            CropSlider(
                label = "Horizontal",
                value = horizontalOffset,
                valueRange = -1f..1f,
                onValueChange = { horizontalOffset = it },
            )
            CropSlider(
                label = "Vertical",
                value = verticalOffset,
                valueRange = -1f..1f,
                onValueChange = { verticalOffset = it },
            )
            Text(
                "Frame the photo inside the folder icon shape, then save.",
                color = Color(0xFFD8C5D4),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CropSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderScreen(
    folder: PrivateFolder,
    apps: List<LaunchableApp>,
    slutModeEnabled: Boolean,
    gridColumnCount: Int,
    tileShape: String,
    labelTextColor: String,
    tileSizeDp: Int,
    onBack: () -> Unit,
    onRenameFolder: (String, String) -> Unit,
    onChooseFolderIcon: (String) -> Unit,
    onLaunchApp: (LaunchableApp) -> Unit,
    onMoveToHome: (LaunchableApp) -> Unit,
) {
    val folderLine = remember(slutModeEnabled, folder.id) { SLUT_MODE_FOLDER_LINES.random() }
    var showRenameDialog by remember(folder.id) { mutableStateOf(false) }
    var draftName by remember(folder.id) { mutableStateOf(folder.name) }
    var draggedApp by remember { mutableStateOf<DraggedApp?>(null) }
    var homeDropBounds by remember { mutableStateOf<Rect?>(null) }
    val homeDropTargetActive = draggedApp?.pointerPosition?.let { pointerPosition ->
        homeDropBounds?.contains(pointerPosition) == true
    } == true

    fun finishFolderDrag() {
        val drag = draggedApp ?: return
        if (homeDropBounds?.contains(drag.pointerPosition) == true) {
            onMoveToHome(drag.app)
        }
        draggedApp = null
    }

    Scaffold(
        containerColor = Color(0xFF0B070D),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(folder.name)
                        Text(
                            text = if (slutModeEnabled) folderLine else "${apps.size} apps",
                            color = if (slutModeEnabled) Color(0xFFFFD38A) else Color(0xFFB9A7B5),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (slutModeEnabled) {
                            Text(
                                text = "${apps.size} private indulgences",
                                color = Color(0xFFB9A7B5),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onChooseFolderIcon(folder.id) }) {
                        Icon(Icons.Rounded.Image, contentDescription = "Choose folder photo")
                    }
                    IconButton(
                        onClick = {
                            draftName = folder.name
                            showRenameDialog = true
                        },
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Rename folder")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0B070D)),
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumnCount),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (draggedApp != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HomeDropTarget(
                        active = homeDropTargetActive,
                        onBoundsChanged = { homeDropBounds = it },
                    )
                }
            }
            if (apps.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Folder is empty", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Drag apps from the Spaces homepage to add them here.",
                            color = Color(0xFFD8C5D4),
                        )
                    }
                }
            } else {
                items(apps, key = { it.componentKey }) { app ->
                    PrivateAppTile(
                        app = app,
                        tileShape = tileShape,
                        labelTextColor = labelTextColor,
                        tileSizeDp = tileSizeDp,
                        modifier = Modifier.animateItem(),
                        isDragging = draggedApp?.app?.componentKey == app.componentKey,
                        onDragStart = { pointerPosition ->
                            draggedApp = DraggedApp(app, 0, pointerPosition)
                        },
                        onDrag = { dragAmount ->
                            draggedApp = draggedApp?.let {
                                it.copy(pointerPosition = it.pointerPosition + dragAmount)
                            }
                        },
                        onDragEnd = ::finishFolderDrag,
                        onClick = { onLaunchApp(app) },
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename folder") },
            text = {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Folder name") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = draftName.trim().isNotEmpty(),
                    onClick = {
                        onRenameFolder(folder.id, draftName)
                        showRenameDialog = false
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF181219),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderPickerScreen(
    app: LaunchableApp,
    folders: List<PrivateFolder>,
    currentFolderId: String?,
    onCreateFolder: () -> Unit,
    onSelectFolder: (String?) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = Color(0xFF0B070D),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Move to folder")
                        Text(
                            app.label,
                            color = Color(0xFFB9A7B5),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0B070D)),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                FolderChoiceRow(
                    title = "Create new folder",
                    subtitle = "Make a new private collection for this app.",
                    selected = false,
                    onClick = onCreateFolder,
                )
            }
            item {
                FolderChoiceRow(
                    title = "No folder",
                    subtitle = "Show this app directly on the Spaces homepage.",
                    selected = currentFolderId == null,
                    onClick = { onSelectFolder(null) },
                )
            }
            items(folders, key = { it.id }) { folder ->
                FolderChoiceRow(
                    title = folder.name,
                    subtitle = "Move into this folder.",
                    selected = currentFolderId == folder.id,
                    onClick = { onSelectFolder(folder.id) },
                )
            }
        }
    }
}

@Composable
private fun FolderChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFF392036) else Color(0xFF181219),
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) Color(0xFFFF6BAA) else Color(0xFF2B222D)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = if (selected) Color(0xFF21101D) else Color(0xFFFFC3D9),
                )
            }
            Spacer(modifier = Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    color = Color(0xFFB9A7B5),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Color(0xFFFFC3D9))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerScreen(
    apps: List<LaunchableApp>,
    selectedComponents: Set<String>,
    onBack: () -> Unit,
    onToggleApp: (LaunchableApp) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val normalizedQuery = query.trim().lowercase(Locale.getDefault())
    val visibleApps = remember(apps, selectedComponents, normalizedQuery) {
        apps.asSequence()
            .filter { app ->
                normalizedQuery.isEmpty() ||
                    app.label.lowercase(Locale.getDefault()).contains(normalizedQuery) ||
                    app.packageName.lowercase(Locale.getDefault()).contains(normalizedQuery)
            }
            .sortedWith(
                compareBy<LaunchableApp> { it.componentKey !in selectedComponents }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label },
            )
            .toList()
    }

    Scaffold(
        containerColor = Color(0xFF0B070D),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Choose apps")
                        Text(
                            text = "${selectedComponents.size} selected",
                            color = Color(0xFFB9A7B5),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0B070D)),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    placeholder = { Text("Search apps") },
                )
            }

            if (visibleApps.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF181219)),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("No matching apps", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Try another app name or package.",
                                color = Color(0xFFB9A7B5),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            items(visibleApps, key = { it.componentKey }) { app ->
                val selected = app.componentKey in selectedComponents
                Card(
                    onClick = { onToggleApp(app) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) Color(0xFF392036) else Color(0xFF181219),
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) Color(0xFF2D182C) else Color(0xFF2B222D))
                                .padding(5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = rememberDrawablePainter(app.icon),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Spacer(modifier = Modifier.size(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                app.packageName,
                                color = Color(0xFFB9A7B5),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF6BAA)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = Color(0xFF21101D),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
