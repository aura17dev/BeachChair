/*
 * Copyright (C) 2025 Lawnchair Launcher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.popup

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.ShortcutUtil
import java.util.stream.Stream

/**
 * Concrete [PopupDataRepository] that builds [PopupData] entries from a set of [ItemInfo]
 * objects. Currently surfaces the App Info system shortcut as an intent-based entry.
 * As the AOSP new-popup architecture matures, additional shortcuts can be added here using
 * the [PopupData] model instead of the legacy [SystemShortcut] click-listener pattern.
 */
class LauncherPopupDataRepository(private val itemInfos: Array<out ItemInfo>) : PopupDataRepository {

    private val dataByType: Map<PoppableType, List<PopupData>> by lazy { buildDataMap() }

    private fun buildDataMap(): Map<PoppableType, List<PopupData>> {
        val result = mutableMapOf<PoppableType, MutableList<PopupData>>()
        for (info in itemInfos) {
            val type = info.toPoppableType() ?: continue
            val entries = buildEntriesFor(info)
            if (entries.isNotEmpty()) {
                result.getOrPut(type) { mutableListOf() }.addAll(entries)
            }
        }
        return result
    }

    private fun buildEntriesFor(info: ItemInfo): List<PopupData> {
        if (!ShortcutUtil.supportsShortcuts(info)) return emptyList()
        val pkg = info.targetPackage ?: return emptyList()
        return listOf(
            PopupData(
                iconResId = android.R.drawable.ic_dialog_info,
                labelResId = R.string.app_info_drop_target_label,
                intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", pkg, null)
                },
                category = PopupCategory.SYSTEM_SHORTCUT,
            )
        )
    }

    override fun getAllPopupData(): Map<PoppableType, Stream<PopupData>> =
        dataByType.mapValues { (_, list) -> list.stream() }

    override fun getPopupDataByType(type: PoppableType): Stream<PopupData> =
        dataByType[type]?.stream() ?: Stream.empty()
}

private fun ItemInfo.toPoppableType(): PoppableType? = when (itemType) {
    ITEM_TYPE_APPLICATION, ITEM_TYPE_SHORTCUT, ITEM_TYPE_DEEP_SHORTCUT -> PoppableType.APP
    ITEM_TYPE_FOLDER -> PoppableType.FOLDER
    ITEM_TYPE_APPWIDGET -> PoppableType.WIDGET
    else -> null
}
