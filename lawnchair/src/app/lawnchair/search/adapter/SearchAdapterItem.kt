package app.lawnchair.search.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.view.View
import app.lawnchair.allapps.views.SearchItemBackground
import app.lawnchair.allapps.views.SearchResultView
import app.lawnchair.search.LawnchairSearchAdapterProvider
import com.android.launcher3.R
import com.android.launcher3.allapps.BaseAllAppsAdapter

data class SearchAdapterItem(
    val searchTarget: SearchTargetCompat,
    val background: SearchItemBackground?,
    val viewType: Int,
) : BaseAllAppsAdapter.AdapterItem(viewType) {

    override fun isSameAs(other: BaseAllAppsAdapter.AdapterItem): Boolean {
        return other is SearchAdapterItem && other.searchTarget.id == searchTarget.id
    }

    override fun isContentSame(other: BaseAllAppsAdapter.AdapterItem): Boolean {
        if (other !is SearchAdapterItem) return false
        // Deliberately NOT `searchTarget == other.searchTarget`: SearchTargetCompat is a data
        // class holding a Bundle, and Bundle only has reference equality — targets are rebuilt
        // on every keystroke, so data-class equals is always false and DiffUtil rebinds every
        // visible row per keystroke (measured: 10% janky frames, 44 ms stalls while typing).
        // Compare the fields that actually affect how the row renders instead.
        return other.viewType == viewType &&
            other.background === background &&
            other.searchTarget.id == searchTarget.id &&
            other.searchTarget.resultType == searchTarget.resultType &&
            other.searchTarget.layoutType == searchTarget.layoutType &&
            other.searchTarget.packageName == searchTarget.packageName &&
            other.searchTarget.userHandle == searchTarget.userHandle &&
            other.searchTarget.extras.getBoolean(SearchResultView.EXTRA_QUICK_LAUNCH, false) ==
            searchTarget.extras.getBoolean(SearchResultView.EXTRA_QUICK_LAUNCH, false)
    }

    fun setRippleEffect(child: View) {
        val shape = RoundRectShape(background?.cornerRadii, null, null)
        val maskDrawable = ShapeDrawable(shape).apply {
            paint.color = Color.WHITE
        }
        val rippleColor = background?.focusHighlight ?: background?.groupHighlight ?: 0
        val inset = child.resources.getDimensionPixelSize(R.dimen.search_decoration_padding)
        val insetMask = InsetDrawable(maskDrawable, 0, inset, 0, inset)
        child.background = RippleDrawable(ColorStateList.valueOf(rippleColor), null, insetMask)
    }

    companion object {

        fun createAdapterItem(
            target: SearchTargetCompat,
            background: SearchItemBackground?,
        ): SearchAdapterItem? {
            val type = LawnchairSearchAdapterProvider.viewTypeMap[target.layoutType] ?: return null
            return SearchAdapterItem(target, background, type)
        }
    }
}
