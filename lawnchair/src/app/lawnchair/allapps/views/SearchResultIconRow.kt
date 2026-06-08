package app.lawnchair.allapps.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.lawnchair.allapps.views.SearchResultView.Companion.FLAG_HIDE_SUBTITLE
import app.lawnchair.font.FontManager
import app.lawnchair.search.adapter.CALCULATOR
import app.lawnchair.search.adapter.HISTORY
import app.lawnchair.search.adapter.SETTINGS
import app.lawnchair.search.adapter.SearchTargetCompat
import app.lawnchair.search.adapter.WEB_SUGGESTION
import app.lawnchair.util.copyToClipboard
import com.android.app.search.LayoutType
import com.android.launcher3.R
import com.android.launcher3.views.BubbleTextHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SearchResultIconRow(context: Context, attrs: AttributeSet?) :
    LinearLayout(context, attrs),
    SearchResultView,
    BubbleTextHolder {

    private var isSmall = false
    private lateinit var icon: SearchResultIcon
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private var delimiter: View? = null
    private lateinit var shortcutIcons: Array<SearchResultIcon>

    private var flags = 0

    // Cached view references and dimension values — set once in onFinishInflate, never re-looked-up per bind.
    private lateinit var textRows: LinearLayout
    private var rowMediumHeight = 0
    private var rowSmallHeight = 0
    private var subtitleStartPadding = 0

    override fun onFinishInflate() {
        super.onFinishInflate()
        isSmall = id == R.id.search_result_small_icon_row
        icon = ViewCompat.requireViewById(this, R.id.icon)
        icon.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        val iconSize = icon.iconSize
        icon.layoutParams.apply {
            width = iconSize
            height = iconSize
        }
        icon.setTextVisibility(false)
        title = ViewCompat.requireViewById(this, R.id.title)
        subtitle = ViewCompat.requireViewById(this, R.id.subtitle)
        subtitle.isVisible = false
        val fontManager = FontManager.INSTANCE.get(context)
        fontManager.setCustomFont(title, R.id.font_heading)
        fontManager.setCustomFont(subtitle, R.id.font_body)
        delimiter = findViewById(R.id.delimiter)
        textRows = ViewCompat.requireViewById(this, R.id.text_rows)
        rowMediumHeight = resources.getDimensionPixelSize(R.dimen.search_result_row_medium_height)
        rowSmallHeight = resources.getDimensionPixelSize(R.dimen.search_result_small_row_height)
        subtitleStartPadding = resources.getDimensionPixelSize(R.dimen.search_result_subtitle_padding_start)
        setOnClickListener(icon)

        shortcutIcons = listOf(
            R.id.shortcut_0,
            R.id.shortcut_1,
            R.id.shortcut_2,
        )
            .mapNotNull { findViewById<SearchResultIcon>(it) }
            .toTypedArray()
        shortcutIcons.forEach {
            it.setTextVisibility(false)
            it.layoutParams.apply {
                width = icon.iconSize
                height = icon.iconSize
            }
        }
    }

    override val isQuickLaunch get() = icon.isQuickLaunch || hasFlag(flags, SearchResultView.FLAG_QUICK_LAUNCH)
    override val titleText get() = if (icon.titleText != "") icon.titleText else title.text

    override fun launch(): Boolean {
        performClick()
        return true
    }

    override fun bind(target: SearchTargetCompat, shortcuts: List<SearchTargetCompat>) {
        flags = getFlags(target.extras)

        icon.bind(target) {
            title.text = it.title
            tag = it
        }
        val isSuggestion = (target.layoutType == LayoutType.HORIZONTAL_MEDIUM_TEXT || target.layoutType == LayoutType.WIDGET_LIVE) &&
            target.resultType == SearchTargetCompat.RESULT_TYPE_SUGGESTIONS &&
            (target.packageName == WEB_SUGGESTION || target.packageName == HISTORY)

        val isSetting = target.layoutType == LayoutType.ICON_SLICE &&
            target.resultType == SearchTargetCompat.RESULT_TYPE_SETTING_TILE &&
            target.packageName == SETTINGS

        val isCalculator = target.layoutType == LayoutType.CALCULATOR &&
            target.resultType == SearchTargetCompat.RESULT_TYPE_CALCULATOR &&
            target.packageName == CALCULATOR

        bindShortcuts(shortcuts)
        var showDelimiter = true
        if (isSmall) {
            if (target.layoutType == LayoutType.HORIZONTAL_MEDIUM_TEXT) {
                showDelimiter = false
                layoutParams.height = rowMediumHeight
                textRows.orientation = VERTICAL
                subtitle.setPadding(0, 0, 0, 0)
            } else {
                layoutParams.height = rowSmallHeight
                textRows.orientation = HORIZONTAL
                subtitle.setPaddingRelative(subtitleStartPadding, 0, 0, 0)
            }
        }
        setSubtitleText(target.searchAction?.subtitle, showDelimiter)
        if (shouldHandleClick(target) && !isSmall) {
            setOnClickListener {
                target.searchAction?.intent?.let { intent -> handleSearchTargetClick(context, intent) }
            }
        }
        if (isSuggestion || isSetting) {
            layoutParams.height = rowSmallHeight
            setOnClickListener {
                target.searchAction?.intent?.let { intent -> handleSearchTargetClick(context, intent) }
            }
        }
        if (isCalculator) {
            setOnClickListener {
                copyToClipboard(
                    context = context,
                    text = target.extras.getString("result").toString(),
                    toastMessage = context.getString(R.string.calculator_search_result_copied_toast),
                )
            }
        }
        if (target.packageName == HISTORY) {
            setOnLongClickListener {
                showDeleteHistoryDialog(context, target)
                true
            }
        } else {
            setOnLongClickListener(null)
        }
    }

    private fun showDeleteHistoryDialog(context: Context, target: SearchTargetCompat) {
        val queryText = target.searchAction?.title?.toString() ?: ""
        if (queryText.isEmpty()) return

        val title = context.getString(R.string.search_history_delete_title)
        val message = context.getString(R.string.search_history_delete_message, queryText)

        android.app.AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val uri = android.net.Uri.parse("content://${app.lawnchair.search.LawnchairRecentSuggestionProvider.AUTHORITY}/suggestions")
                val scope = (context as? LifecycleOwner)?.lifecycleScope
                if (scope != null) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            context.contentResolver.delete(uri, "display1 = ?", arrayOf(queryText))
                        } catch (e: Exception) {
                            android.util.Log.e("SearchResultIconRow", "Failed to delete history item", e)
                        }
                        launch(Dispatchers.Main) {
                            val activityContext = com.android.launcher3.views.ActivityContext.lookupContext<com.android.launcher3.BaseActivity>(context) as com.android.launcher3.views.ActivityContext
                            activityContext.appsView?.searchUiManager?.refreshResults()
                        }
                    }
                } else {
                    try {
                        context.contentResolver.delete(uri, "display1 = ?", arrayOf(queryText))
                        val activityContext = com.android.launcher3.views.ActivityContext.lookupContext<com.android.launcher3.BaseActivity>(context) as com.android.launcher3.views.ActivityContext
                        activityContext.appsView?.searchUiManager?.refreshResults()
                    } catch (e: Exception) {
                        android.util.Log.e("SearchResultIconRow", "Failed to delete history item", e)
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setSubtitleText(subtitleText: CharSequence?, showDelimiter: Boolean) {
        if (subtitleText.isNullOrEmpty() || icon.hasFlag(FLAG_HIDE_SUBTITLE)) {
            subtitle.isVisible = false
            delimiter?.isVisible = false
        } else {
            subtitle.text = subtitleText
            subtitle.isVisible = true
            delimiter?.isVisible = showDelimiter
        }
    }

    private fun bindShortcuts(shortcuts: List<SearchTargetCompat>) {
        shortcutIcons.forEachIndexed { index, icon ->
            if (index < shortcuts.size) {
                icon.isVisible = true
                icon.bind(shortcuts[index], emptyList())
            } else {
                icon.isVisible = false
            }
        }
    }

    override fun getBubbleText() = icon
}
