package app.lawnchair.allapps.pages

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Surface
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.launcher3.R
import com.android.launcher3.model.data.FolderInfo
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.Palette
import kotlin.math.abs
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.preferences2.asState

@Composable
fun DrawerPageTabBar(
    pages: List<FolderInfo>,
    selectedPageId: Int?,
    isBulkSelectMode: Boolean,
    selectedCount: Int,
    onPageSelected: (Int?) -> Unit,
    onPageLongPress: (FolderInfo) -> Unit,
    onExitBulkSelect: () -> Unit,
    onMoveSelected: () -> Unit,
    navBarPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        HorizontalDivider(color = dividerColor, thickness = 0.5.dp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(surface),
        ) {
            if (isBulkSelectMode) {
                BulkSelectToolbar(
                    selectedCount = selectedCount,
                    onExit = onExitBulkSelect,
                    onMove = onMoveSelected,
                )
            } else {
                TabRow(
                    pages = pages,
                    selectedPageId = selectedPageId,
                    onPageSelected = onPageSelected,
                    onPageLongPress = onPageLongPress,
                )
            }
        }

        // Fills the navigation bar area so the surface colour extends behind it.
        // Uses a static height (measured once at setup) to avoid Compose recompositions
        // triggered by window inset callbacks during the drawer close/snap-back animation,
        // which caused a ghosted double-image rendering artifact.
        if (navBarPadding > 0.dp) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surface)
                    .height(navBarPadding),
            )
        }
    }
}

@Composable
private fun TabRow(
    pages: List<FolderInfo>,
    selectedPageId: Int?,
    onPageSelected: (Int?) -> Unit,
    onPageLongPress: (FolderInfo) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val hapticFeedbackEnabledState = preferenceManager2().appDrawerHapticFeedback.asState()

    // Memoize by pages reference: avoids rebuilding the list (and re-keying pointerInput,
    // which cancels any in-progress gesture) on every recomposition where pages didn't change.
    val pagesList = remember(pages) { listOf<Int?>(null) + pages.map { it.id } }
    val selectedIndex = remember(pagesList, selectedPageId) {
        pagesList.indexOf(selectedPageId).coerceAtLeast(0)
    }
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    // Overflow state — recompose only when the scroll boundary actually changes.
    val canScrollLeft  by remember { derivedStateOf { listState.canScrollBackward } }
    val canScrollRight by remember { derivedStateOf { listState.canScrollForward } }

    // Auto-scroll to keep the active tab visible when the selection changes from
    // outside (e.g. app launch, rename). Skip while edge-scroll is driving the list.
    var isEdgeScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(selectedIndex) {
        if (!isEdgeScrolling) {
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.none { it.index == selectedIndex }) {
                listState.scrollToItem(selectedIndex.coerceIn(0, pagesList.lastIndex))
            }
        }
    }

    val tabBounds = remember { mutableStateMapOf<Int, IntRange>() }
    var rowCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val surface = MaterialTheme.colorScheme.surfaceContainer
    val edgeZonePx  = with(density) { 52.dp.toPx() }
    val scrollStepPx = with(density) { 5.dp.toPx() }

    // -1f = scroll left, 0f = stopped, 1f = scroll right.
    // Written by the gesture handler (restricted scope); read by the LaunchedEffect below
    // (unrestricted scope) so we can call suspend functions like scrollBy/delay.
    var edgeScrollDir by remember { mutableStateOf(0f) }
    LaunchedEffect(edgeScrollDir) {
        if (edgeScrollDir != 0f) {
            while (true) {
                withFrameMillis { }
                listState.scroll { scrollBy(edgeScrollDir * scrollStepPx) }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .onGloballyPositioned { rowCoords = it }
            .pointerInput(pagesList) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    val startY = down.position.y
                    var scrubbing = false
                    var lastIdx = -1

                    while (true) {
                        val event = awaitPointerEvent()
                        val ptr = event.changes.find { it.id == down.id } ?: break
                        if (!ptr.pressed) {
                            edgeScrollDir = 0f
                            isEdgeScrolling = false
                            break
                        }

                        val dx = abs(ptr.position.x - startX)
                        val dy = abs(ptr.position.y - startY)
                        if (!scrubbing && dx > viewConfiguration.touchSlop && dx > dy * 1.5f) {
                            scrubbing = true
                        }

                        if (scrubbing) {
                            val x = ptr.position.x
                            val rowWidth = rowCoords?.size?.width?.toFloat() ?: Float.MAX_VALUE

                            val inRight = x > rowWidth - edgeZonePx && listState.canScrollForward
                            val inLeft  = x < edgeZonePx && listState.canScrollBackward

                            val newDir = when {
                                inRight -> 1f
                                inLeft  -> -1f
                                else    -> 0f
                            }
                            if (newDir != edgeScrollDir) {
                                edgeScrollDir = newDir
                                isEdgeScrolling = newDir != 0f
                            }

                            // Select the tab under the finger at all times — including
                            // while edge-scrolling so newly revealed tabs get picked up.
                            val touchX = x.toInt()
                            val idx = tabBounds.entries.firstOrNull { (_, r) -> touchX in r }?.key
                            if (idx != null && idx < pagesList.size && idx != lastIdx) {
                                val oldIdx = lastIdx
                                lastIdx = idx
                                onPageSelected(pagesList[idx])
                                if (oldIdx != -1 && hapticFeedbackEnabledState.value) {
                                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                }
                            }

                            ptr.consume()
                        }
                    }
                }
            },
    ) {
        LazyRow(
            state = listState,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            item(key = "all") {
                M3Tab(
                    selected = selectedPageId == null,
                    onClick = { onPageSelected(null) },
                    onLongClick = null,
                    onBoundsChanged = { coords ->
                        rowCoords?.let { row ->
                            val x = row.localPositionOf(coords, Offset.Zero).x.toInt()
                            tabBounds[0] = x..(x + coords.size.width)
                        }
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Lucide.LayoutGrid,
                            contentDescription = "All apps",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "All",
                            fontSize = 17.sp,
                            fontWeight = if (selectedPageId == null) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            pages.forEachIndexed { i, page ->
                item(key = page.id) {
                    M3Tab(
                        selected = selectedPageId == page.id,
                        onClick = { onPageSelected(page.id) },
                        onLongClick = { onPageLongPress(page) },
                        onBoundsChanged = { coords ->
                            rowCoords?.let { row ->
                                val x = row.localPositionOf(coords, Offset.Zero).x.toInt()
                                tabBounds[i + 1] = x..(x + coords.size.width)
                            }
                        },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val iconName = page.icon
                            val iconVector = if (iconName != null) LucideIconsMap[iconName] else Lucide.Folder
                            val showText = !page.iconOnly || iconName == null
                            if (iconVector != null) {
                                val iconSize = if (showText) 18.dp else 24.dp
                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = if (showText) null else (page.title?.toString() ?: "Page"),
                                    modifier = Modifier.size(iconSize),
                                )
                                if (showText) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                            }
                            if (showText) {
                                Text(
                                    text = page.title?.toString() ?: "Page",
                                    fontSize = 17.sp,
                                    fontWeight = if (selectedPageId == page.id) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Left overflow indicator — shown when tabs are hidden to the left.
        if (canScrollLeft) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(52.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(listOf(surface, Color.Transparent)),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "‹",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Light,
                )
            }
        }

        // Right overflow indicator — shown when tabs are hidden to the right.
        if (canScrollRight) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(52.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(listOf(Color.Transparent, surface)),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "›",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Light,
                )
            }
        }
    }
}

@Composable
private fun M3Tab(
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onBoundsChanged: ((LayoutCoordinates) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textColor by animateColorAsState(
        targetValue = if (selected) selectedContentColor else unselectedContentColor,
        animationSpec = tween(40),
        label = "tabTextColor",
    )
    val pillColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = tween(40),
        label = "tabPillColor",
    )

    Box(
        modifier = modifier
            .height(72.dp)
            .wrapContentWidth()
            // Report full tap-target bounds (before clip) for the scrub hit-test.
            .then(if (onBoundsChanged != null) Modifier.onGloballyPositioned(onBoundsChanged) else Modifier)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        // Animated pill sits behind the label, sized to match the full tab tap area.
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 4.dp, vertical = 14.dp)
                .clip(RoundedCornerShape(50))
                .background(pillColor),
        )
        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            CompositionLocalProvider(LocalContentColor provides textColor) {
                content()
            }
        }
    }
}

@Composable
private fun BulkSelectToolbar(
    selectedCount: Int,
    onExit: () -> Unit,
    onMove: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (selectedCount == 0) "Tap apps to select" else "$selectedCount selected",
            color = onSurface.copy(alpha = if (selectedCount == 0) 0.5f else 0.85f),
            fontSize = 17.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )

        // Move button — filled pill, only active when something is selected
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (selectedCount > 0) primary else onSurface.copy(alpha = 0.12f))
                .clickable(enabled = selectedCount > 0) { onMove() }
                .padding(horizontal = 18.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Move to page",
                color = if (selectedCount > 0) MaterialTheme.colorScheme.onPrimary
                        else onSurface.copy(alpha = 0.35f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.width(8.dp))

        // Cancel — ghost text
        Text(
            text = "Cancel",
            color = onSurface.copy(alpha = 0.55f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { onExit() }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
internal fun PageMenuButton(
    showAddApps: Boolean,
    onNewPage: () -> Unit,
    onAddApps: () -> Unit,
    onSelectApps: (() -> Unit)?,
    onAiSort: () -> Unit,
    onIconStyle: () -> Unit,
    onWipePages: () -> Unit,
    onLawnchairSettings: () -> Unit,
    onDeviceSettings: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showWipeConfirm by remember { mutableStateOf(false) }

    if (showWipeConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text("Clear all pages?") },
            text = { Text("This will permanently delete all drawer pages. Apps will remain in All Apps.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showWipeConfirm = false; onWipePages() }) {
                    Text("Clear all", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showWipeConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
    val popupOffsetY = with(LocalDensity.current) { 40.dp.roundToPx() }

    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.drawer_page_menu_btn_desc),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (menuExpanded) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(x = 0, y = popupOffsetY),
                onDismissRequest = { menuExpanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 8.dp,
                    tonalElevation = 3.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .width(IntrinsicSize.Max)
                            .widthIn(min = 200.dp)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PageMenuPillItem(
                            label = stringResource(R.string.drawer_page_menu_new_page),
                            onClick = { menuExpanded = false; onNewPage() },
                        )
                        if (showAddApps) {
                            PageMenuPillItem(
                                label = stringResource(R.string.drawer_page_menu_add_apps),
                                onClick = { menuExpanded = false; onAddApps() },
                            )
                        }
                        if (onSelectApps != null) {
                            PageMenuPillItem(
                                label = stringResource(R.string.drawer_page_menu_select_apps),
                                onClick = { menuExpanded = false; onSelectApps() },
                            )
                        }
                        PageMenuPillItem(
                            label = stringResource(R.string.drawer_page_menu_ai_sort),
                            onClick = { menuExpanded = false; onAiSort() },
                        )
                        PageMenuPillItem(
                            label = stringResource(R.string.drawer_page_menu_icon_style),
                            onClick = { menuExpanded = false; onIconStyle() },
                        )
                        PageMenuPillItem(
                            label = stringResource(R.string.drawer_page_menu_wipe_pages),
                            onClick = { menuExpanded = false; showWipeConfirm = true },
                            isDestructive = true,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 2.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        PageMenuPillItem(
                            label = stringResource(R.string.drawer_page_menu_lawnchair_settings),
                            onClick = { menuExpanded = false; onLawnchairSettings() },
                        )
                        PageMenuPillItem(
                            label = stringResource(R.string.drawer_page_menu_device_settings),
                            onClick = { menuExpanded = false; onDeviceSettings() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageMenuPillItem(label: String, onClick: () -> Unit, isDestructive: Boolean = false) {
    val bgColor = if (isDestructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val textColor = if (isDestructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
