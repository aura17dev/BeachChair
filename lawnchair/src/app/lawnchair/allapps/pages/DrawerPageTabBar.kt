package app.lawnchair.allapps.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary

    if (isBulkSelectMode) {
        BulkSelectToolbar(
            selectedCount = selectedCount,
            onExit = onExitBulkSelect,
            onMove = onMoveSelected,
            surfaceColor = surfaceColor,
            onSurfaceColor = onSurfaceColor,
            primaryColor = primaryColor,
            modifier = modifier,
        )
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(surfaceColor)
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PageTab(
            title = "All",
            isSelected = selectedPageId == null,
            onClick = { onPageSelected(null) },
            onLongPress = null,
            surfaceColor = surfaceColor,
            onSurfaceColor = onSurfaceColor,
            primaryColor = primaryColor,
        )

        pages.forEach { page ->
            PageTab(
                title = page.title?.toString() ?: "Page",
                isSelected = selectedPageId == page.id,
                onClick = { onPageSelected(page.id) },
                onLongPress = { onPageLongPress(page) },
                surfaceColor = surfaceColor,
                onSurfaceColor = onSurfaceColor,
                primaryColor = primaryColor,
            )
        }

        Box(
            modifier = Modifier
                .width(40.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(primaryColor.copy(alpha = 0.12f))
                .clickable { onCreatePage() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                color = primaryColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PageTab(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?,
    surfaceColor: Color,
    onSurfaceColor: Color,
    primaryColor: Color,
) {
    val bgColor = if (isSelected) primaryColor.copy(alpha = 0.15f) else Color.Transparent
    val textColor = if (isSelected) primaryColor else onSurfaceColor.copy(alpha = 0.7f)

    Box(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .then(
                if (onLongPress != null) {
                    Modifier.clickable(onClick = {}) // long press handled differently
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
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
    surfaceColor: Color,
    onSurfaceColor: Color,
    primaryColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(primaryColor.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$selectedCount selected",
            color = onSurfaceColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = "MOVE",
            color = primaryColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { if (selectedCount > 0) onMove() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )

        Text(
            text = "CANCEL",
            color = onSurfaceColor.copy(alpha = 0.6f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onExit() }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}
