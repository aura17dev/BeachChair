package app.lawnchair.allapps.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.launcher3.R
import com.android.launcher3.model.data.FolderInfo

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
    onCreatePage: () -> Unit,
    onAddApps: (() -> Unit)?,
    navBarPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val surface = MaterialTheme.colorScheme.surface
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
                    onCreatePage = onCreatePage,
                    onAddApps = onAddApps,
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
    onCreatePage: () -> Unit,
    onAddApps: (() -> Unit)?,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // "All" tab
        PageTab(
            title = "All",
            isSelected = selectedPageId == null,
            onClick = { onPageSelected(null) },
            onLongClick = null,
            primary = primary,
            onSurface = onSurface,
        )

        pages.forEach { page ->
            PageTab(
                title = page.title?.toString() ?: "Page",
                isSelected = selectedPageId == page.id,
                onClick = { onPageSelected(page.id) },
                onLongClick = { onPageLongPress(page) },
                primary = primary,
                onSurface = onSurface,
            )
        }

        // "+ Add apps" — only shown when a page is active
        if (selectedPageId != null && onAddApps != null) {
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(primary.copy(alpha = 0.12f))
                    .clickable { onAddApps() }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+ Add",
                    color = primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.width(4.dp))

        // "+" create page button
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(onSurface.copy(alpha = 0.08f))
                .clickable { onCreatePage() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                color = onSurface.copy(alpha = 0.7f),
                fontSize = 20.sp,
                fontWeight = FontWeight.Light,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun PageTab(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    primary: Color,
    onSurface: Color,
) {
    val bgColor = if (isSelected) primary.copy(alpha = 0.15f) else Color.Transparent
    val textColor = if (isSelected) primary else onSurface.copy(alpha = 0.6f)
    val weight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = weight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
            fontSize = 14.sp,
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
