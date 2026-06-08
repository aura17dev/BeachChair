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

import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Launcher
import com.android.launcher3.dragndrop.DragOptions
import java.lang.ref.WeakReference

/**
 * Bridge implementation of [PopupController] for [BubbleTextView] icons. Delegates to the
 * existing [PopupContainerWithArrow] system while the new AOSP popup architecture matures.
 * The [popupDataRepository] passed to [show] is intentionally unused here; the container
 * fetches its own data via the launcher's getSupportedShortcuts path.
 */
class BubbleTextViewPopupController(view: BubbleTextView) : PopupController {

    private val viewRef = WeakReference(view)

    override fun show(popupDataRepository: PopupDataRepository): Popup? {
        val view = viewRef.get() ?: return null
        val container = PopupContainerWithArrow.showForIcon(view) ?: return null
        return PopupContainerWrapper(container)
    }

    override fun dismiss() {
        val view = viewRef.get() ?: return
        AbstractFloatingView.closeOpenViews(
            Launcher.getLauncher(view.context),
            /* animate= */ true,
            AbstractFloatingView.TYPE_ACTION_POPUP,
        )
    }
}

private class PopupContainerWrapper(
    private val container: PopupContainerWithArrow<*>,
) : Popup {
    override fun createPreDragCondition(): DragOptions.PreDragCondition? =
        container.createPreDragCondition(/* updateIconUi= */ true)
}
