package app.lawnchair.allapps

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.provider.SearchRecentSuggestions
import android.text.Selection
import android.text.SpannableStringBuilder
import android.text.Spanned.SPAN_POINT_MARK
import android.text.method.TextKeyListener
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.ViewTreeObserver
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import app.lawnchair.allapps.pages.AddAppsToPageSheet
import app.lawnchair.allapps.pages.AppListItem
import app.lawnchair.allapps.pages.CreatePageSheet
import app.lawnchair.allapps.pages.DrawerBulkSelectController
import app.lawnchair.allapps.pages.DrawerPageTabBar
import app.lawnchair.allapps.pages.DrawerPageViewModel
import app.lawnchair.allapps.pages.MoveToPageSheet
import app.lawnchair.allapps.pages.RenamePageSheet
import app.lawnchair.launcher
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.subscribeBlocking
import app.lawnchair.qsb.AssistantIconView
import app.lawnchair.qsb.LawnQsbLayout.Companion.getLensIntent
import app.lawnchair.qsb.LawnQsbLayout.Companion.getSearchProvider
import app.lawnchair.qsb.ThemingMethod
import app.lawnchair.qsb.providers.Google
import app.lawnchair.qsb.providers.GoogleGo
import app.lawnchair.qsb.providers.PixelSearch
import app.lawnchair.qsb.setThemedIconResource
import app.lawnchair.search.LawnchairRecentSuggestionProvider
import app.lawnchair.search.algorithms.LawnchairSearchAlgorithm
import app.lawnchair.theme.drawable.DrawableTokens
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.util.viewAttachedScope
import app.lawnchair.views.ComposeBottomSheet
import com.android.launcher3.Insettable
import com.android.launcher3.InvariantDeviceProfile.OnIDPChangeListener
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.allapps.ActivityAllAppsContainerView
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem
import com.android.launcher3.allapps.SearchUiManager
import com.android.launcher3.allapps.search.AllAppsSearchBarController
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.search.SearchCallback
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Themes
import com.android.systemui.shared.system.BlurUtils
import com.patrykmichalik.opto.core.firstBlocking
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class AllAppsSearchInput(context: Context, attrs: AttributeSet?) :
    FrameLayout(context, attrs),
    Insettable,
    OnIDPChangeListener,
    SearchUiManager,
    SearchCallback<AdapterItem>,
    AllAppsStore.OnUpdateListener,
    ViewTreeObserver.OnGlobalLayoutListener {

    private lateinit var hint: TextView
    private lateinit var input: FallbackSearchInputView
    private lateinit var actionButton: ImageButton
    private lateinit var searchIcon: ImageButton

    private lateinit var micIcon: AssistantIconView
    private lateinit var lensIcon: ImageButton

    private val qsbMarginTopAdjusting = resources.getDimensionPixelSize(R.dimen.qsb_margin_top_adjusting)
    private val allAppsSearchVerticalOffset = resources.getDimensionPixelSize(R.dimen.all_apps_search_vertical_offset)

    private val launcher = context.launcher
    private val searchBarController = AllAppsSearchBarController()
    private val searchQueryBuilder = SpannableStringBuilder().apply {
        Selection.setSelection(this, 0)
    }

    private lateinit var apps: LawnchairAlphabeticalAppsList<*>
    private lateinit var mainApps: LawnchairAlphabeticalAppsList<*>
    private lateinit var appsView: ActivityAllAppsContainerView<*>
    private var searchAlgorithm: LawnchairSearchAlgorithm? = null

    private var focusedResultTitle = ""
    private var canShowHint = false

    private val supportBlur = BlurUtils.supportsBlursOnWindows()
    private val bg = if (supportBlur) {
        DrawableTokens.SearchInputFgBlur.resolve(context)
    } else {
        DrawableTokens.SearchInputFg.resolve(context)
    }
    private val bgAlphaAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 300
        interpolator = DecelerateInterpolator()
    }
    private var bgVisible = true
    private var bgAlpha = 1f
    private val suggestionsRecent = SearchRecentSuggestions(launcher, LawnchairRecentSuggestionProvider.AUTHORITY, LawnchairRecentSuggestionProvider.MODE)
    private val prefs = PreferenceManager.getInstance(launcher)
    private val prefs2 = PreferenceManager2.getInstance(launcher)

    private var initialPaddingLeft: Int = 0
    private var initialPaddingRight: Int = 0

    // 8 dp topMargin + 4 dp handle height + 8 dp gap = 20 dp reserved for the drag handle pill.
    private val drawerHandleAreaPx = (20 * resources.displayMetrics.density).toInt()

    override fun onFinishInflate() {
        super.onFinishInflate()

        val wrapper = ViewCompat.requireViewById<View>(this, R.id.search_wrapper)
        wrapper.background = bg
        setupPadding()
        bgAlphaAnimator.addUpdateListener { updateBgAlpha() }

        hint = ViewCompat.requireViewById(this, R.id.hint)

        input = ViewCompat.requireViewById(this, R.id.input)

        searchIcon = ViewCompat.requireViewById(this, R.id.search_icon)
        micIcon = ViewCompat.requireViewById(this, R.id.mic_btn)
        lensIcon = ViewCompat.requireViewById(this, R.id.lens_btn)

        val shouldShowIcons = prefs2.matchHotseatQsbStyle.firstBlocking()

        val searchProvider = getSearchProvider(context, prefs2)
        val isGoogle = searchProvider == Google || searchProvider == GoogleGo || searchProvider == PixelSearch
        val supportsLens = searchProvider == Google || searchProvider == PixelSearch

        val lensIntent = getLensIntent(context)
        val voiceIntent = AssistantIconView.getVoiceIntent(searchProvider, context)

        micIcon.isVisible = shouldShowIcons && voiceIntent != null
        lensIcon.isVisible = shouldShowIcons && supportsLens && lensIntent != null

        actionButton = ViewCompat.requireViewById(this, R.id.action_btn)
        with(actionButton) {
            isVisible = false
            setOnClickListener {
                input.reset()
                searchAlgorithm?.doZeroStateSearch(this@AllAppsSearchInput)
                updateHint()
            }
        }

        prefs2.themedHotseatQsb.subscribeBlocking(scope = viewAttachedScope) { themed ->
            with(searchIcon) {
                isVisible = true

                val iconRes = if (themed) searchProvider.themedIcon else searchProvider.icon
                val resId = if (shouldShowIcons) iconRes else R.drawable.ic_qsb_search
                val isThemed = themed || resId == R.drawable.ic_qsb_search
                val method = if (shouldShowIcons) searchProvider.themingMethod else ThemingMethod.TINT

                setThemedIconResource(
                    resId = resId,
                    themed = isThemed,
                    method = method,
                )

                setOnClickListener {
                    val launcher = context.launcher
                    launcher.lifecycleScope.launch {
                        searchProvider.launch(launcher)
                    }
                }
            }
            with(micIcon) {
                setIcon(isGoogle, themed)
                setOnClickListener {
                    context.startActivity(voiceIntent)
                }
            }
            with(lensIcon) {
                if (lensIntent != null) {
                    setThemedIconResource(R.drawable.ic_lens_color, themed)
                    setOnClickListener {
                        runCatching { context.startActivity(lensIntent) }
                    }
                }
            }
        }
        val currentPaddingLeft = initialPaddingLeft
        val currentPaddingRight = initialPaddingRight
        input.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                if (prefs2.searchAlgorithm.firstBlocking() != LawnchairSearchAlgorithm.APP_SEARCH) {
                    input.setHint(R.string.all_apps_device_search_hint)
                } else {
                    input.setHint(R.string.all_apps_search_bar_hint)
                }

                if (input.text.toString().isEmpty()) {
                    searchAlgorithm?.doZeroStateSearch(this)
                }

                setBackgroundVisibility(false, 0f)
                animateHintVisibility(true)
                animatePadding(currentPaddingLeft / 2, currentPaddingRight / 2)

                // Explicitly pop the IME as soon as the field gains focus. The framework does
                // not reliably auto-show the keyboard on the first focus inside the launcher
                // window, which is why opening search used to require a second tap. Posting it
                // ensures the window/IME connection is ready before we request it.
                post { input.showKeyboard() }
            } else {
                setBackgroundVisibility(true, 1f)
                animateHintVisibility(false)
                if (prefs.searchResulRecentSuggestion.get()) {
                    val query = editText.text.toString()
                    suggestionsRecent.saveRecentQuery(query, null)
                }

                animatePadding(currentPaddingLeft, currentPaddingRight)
                focusedResultTitle = ""
                input.setHint("")
                hint.text = ""
            }

            if (::appsView.isInitialized) {
                appsView.mSearchRecyclerView.invalidate()
            }
        }

        input.addTextChangedListener(
            beforeTextChanged = { _, _, _, _ ->
                hint.isInvisible = true
            },
            afterTextChanged = {
                updateHint()
                if (input.text.isNullOrEmpty()) {
                    searchAlgorithm?.doZeroStateSearch(this)
                }
                if (input.text.toString() == "/lawnchairdebug") {
                    val enableDebugMenu = prefs.enableDebugMenu
                    enableDebugMenu.set(!enableDebugMenu.get())
                    launcher.stateManager.goToState(LauncherState.NORMAL)
                }

                actionButton.isVisible = !it.isNullOrEmpty()
                micIcon.isVisible = shouldShowIcons && voiceIntent != null && it.isNullOrEmpty()
                lensIcon.isVisible = shouldShowIcons && supportsLens && lensIntent != null && it.isNullOrEmpty()
            },
        )

        val hide = prefs2.hideAppDrawerSearchBar.firstBlocking()
        if (hide) {
            isInvisible = true
            layoutParams.height = 0
        }

        initPageTabs()
    }

    private fun initPageTabs() {
        prefs2.enableDrawerPages.get()
            .onEach { enabled ->
                if (enabled) {
                    showTabBar()
                } else {
                    hideTabBar()
                }
            }
            .launchIn(viewAttachedScope)
    }

    private var tabBarView: androidx.compose.ui.platform.ComposeView? = null
    private var drawerPageViewModel: DrawerPageViewModel? = null
    private var bulkSelectController: DrawerBulkSelectController? = null
    private var tabBarHeightPx: Int = 0

    private fun showTabBar() {
        if (tabBarView != null) return
        if (!::appsView.isInitialized) return
        try {
            val tabBar = androidx.compose.ui.platform.ComposeView(context).apply {
                id = View.generateViewId()
            }
            tabBarView = tabBar

            val params = android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
            )
            params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM)

            appsView.addView(tabBar, params)

            // 52 dp is the tab bar content row height (nav bar inset is handled by AOSP separately).
            tabBarHeightPx = (52 * resources.displayMetrics.density).toInt()
            appsView.setDrawerPageBottomPadding(tabBarHeightPx)

            // Read nav bar height once so the Compose spacer uses a static value.
            // Dynamic windowInsetsBottomHeight re-triggers recomposition when appsView
            // translates during the close gesture, causing a ghost double-image artifact.
            val navBarInsetPx = ViewCompat.getRootWindowInsets(appsView)
                ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
            val navBarPadding = (navBarInsetPx / resources.displayMetrics.density).dp

            val application = context.applicationContext as android.app.Application
            val viewModel = DrawerPageViewModel(application)
            drawerPageViewModel = viewModel

            tabBar.setContent {
                LawnchairTheme {
                    val pages by viewModel.pages.collectAsState()
                    val selectedPageId by viewModel.selectedPageId.collectAsState()
                    val isBulkSelect by viewModel.isBulkSelectMode.collectAsState()
                    val selectedApps by viewModel.selectedApps.collectAsState()

                    DrawerPageTabBar(
                        pages = pages,
                        selectedPageId = selectedPageId,
                        isBulkSelectMode = isBulkSelect,
                        selectedCount = selectedApps.size,
                        navBarPadding = navBarPadding,
                        onPageSelected = { pageId ->
                            viewModel.selectPage(pageId)
                            filterAppsByPage(pageId, pages)
                        },
                        onPageLongPress = { page ->
                            showRenamePageSheet(page, pages, viewModel)
                        },
                        onExitBulkSelect = { viewModel.exitBulkSelectMode() },
                        onMoveSelected = {
                            showMoveToPageSheet(pages, viewModel, selectedApps)
                        },
                        onCreatePage = {
                            showCreatePageSheet(pages, viewModel)
                        },
                        onAddApps = selectedPageId?.let { pageId ->
                            { showAddAppsSheet(pageId, pages, viewModel) }
                        },
                    )
                }
            }

            // Clear page filter when entering bulk select so all apps are visible for selection,
            // then restore the filter when exiting.
            viewModel.isBulkSelectMode
                .onEach { inBulkSelect ->
                    if (inBulkSelect) {
                        mainApps.updateItemFilter(null)
                    } else {
                        filterAppsByPage(viewModel.selectedPageId.value, viewModel.pages.value)
                    }
                }
                .launchIn(viewAttachedScope)

            appsView.activeRecyclerView?.let { rv ->
                bulkSelectController = DrawerBulkSelectController(context, rv, viewModel, viewAttachedScope)
            }
        } catch (e: Exception) {
            android.util.Log.e("DrawerPages", "Failed to show tab bar", e)
        }
    }

    private fun hideTabBar() {
        bulkSelectController?.detach()
        bulkSelectController = null
        drawerPageViewModel = null
        if (tabBarHeightPx > 0) {
            appsView.setDrawerPageBottomPadding(0)
            tabBarHeightPx = 0
        }
        tabBarView?.let { view ->
            if (view.parent is ViewGroup) {
                (view.parent as ViewGroup).removeView(view)
            }
        }
        tabBarView = null
    }

    private fun filterAppsByPage(pageId: Int?, pages: List<FolderInfo>) {
        if (pageId == null) {
            mainApps.updateItemFilter(null)
            return
        }
        val page = pages.find { it.id == pageId } ?: return
        val pageAppKeys = page.getContents().mapNotNull { it.componentKey }.toSet()
        mainApps.updateItemFilter { info ->
            info is com.android.launcher3.model.data.AppInfo && info.toComponentKey() in pageAppKeys
        }
    }

    private fun showCreatePageSheet(pages: List<FolderInfo>, viewModel: DrawerPageViewModel) {
        ComposeBottomSheet.show(context as com.android.launcher3.Launcher) {
            CreatePageSheet(
                existingPages = pages,
                onCreate = { name ->
                    viewModel.createPage(name)
                    close(true)
                },
                onDismiss = { close(true) },
            )
        }
    }

    private fun showRenamePageSheet(
        page: FolderInfo,
        pages: List<FolderInfo>,
        viewModel: DrawerPageViewModel,
    ) {
        ComposeBottomSheet.show(context as com.android.launcher3.Launcher) {
            RenamePageSheet(
                page = page,
                existingPages = pages,
                onRename = { name ->
                    viewModel.renamePage(page.id, name)
                    close(true)
                },
                onDelete = {
                    viewModel.deletePage(page.id)
                    close(true)
                },
                onDismiss = { close(true) },
            )
        }
    }

    private fun showMoveToPageSheet(
        pages: List<FolderInfo>,
        viewModel: DrawerPageViewModel,
        selectedApps: Set<ComponentKey>,
    ) {
        ComposeBottomSheet.show(context as com.android.launcher3.Launcher) {
            MoveToPageSheet(
                pages = pages,
                onCreatePage = {
                    close(true)
                    showCreatePageSheet(pages, viewModel)
                },
                onSelectPage = { page ->
                    viewModel.moveAppsToPage(selectedApps, page.id)
                    close(true)
                },
                onDismiss = { close(true) },
            )
        }
    }

    private fun showAddAppsSheet(
        selectedPageId: Int,
        pages: List<FolderInfo>,
        viewModel: DrawerPageViewModel,
    ) {
        val page = pages.find { it.id == selectedPageId } ?: return
        val pageTitle = page.title?.toString() ?: "Page"
        val existingKeys = page.getContents().mapNotNull { it.componentKey }.toSet()

        val appsStore = appsView.appsStore
        val allApps = appsStore?.getApps() ?: return
        val appList = allApps
            .filter { it.componentKey != null && it.componentKey !in existingKeys }
            .map { AppListItem(it.componentKey!!, it.title?.toString() ?: "Unknown") }
            .sortedBy { it.label.lowercase() }

        ComposeBottomSheet.show(context as com.android.launcher3.Launcher) {
            AddAppsToPageSheet(
                apps = appList,
                pageTitle = pageTitle,
                onConfirm = { selectedKeys ->
                    viewModel.moveAppsToPage(selectedKeys, selectedPageId)
                    close(true)
                },
                onDismiss = { close(true) },
            )
        }
    }

    private fun setupPadding() {
        launcher.deviceProfile.let { dp ->
            val padding = dp.getAllAppsIconStartMargin(context)
            initialPaddingLeft = padding
            initialPaddingRight = padding
            setPadding(padding, paddingTop, padding, paddingBottom)
        }
    }

    private fun animateHintVisibility(visible: Boolean) {
        val targetAlpha = if (visible) 1f else 0f
        val duration = if (visible) 300L else 200L

        if (visible) {
            hint.alpha = 0f
            hint.isVisible = true
        }

        hint.animate()
            .alpha(targetAlpha)
            .setDuration(duration)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction {
                if (!visible) hint.isVisible = false
            }
            .start()
    }

    private fun animatePadding(newPaddingLeft: Int, newPaddingRight: Int) {
        val currentPaddingLeft = paddingLeft
        val currentPaddingRight = paddingRight

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedFraction
                val leftPadding = currentPaddingLeft + (newPaddingLeft - currentPaddingLeft) * fraction
                val rightPadding = currentPaddingRight + (newPaddingRight - currentPaddingRight) * fraction
                setPadding(leftPadding.toInt(), paddingTop, rightPadding.toInt(), paddingBottom)
            }
            start()
        }
    }

    override fun setFocusedResultTitle(title: CharSequence?, sub: CharSequence?, showArrow: Boolean) {
        focusedResultTitle = title?.toString().orEmpty()
        updateHint()
    }

    override fun refreshResults() {
        onAppsUpdated()
    }

    private fun updateHint() {
        val inputString = input.text.toString()
        val inputLowerCase = inputString.lowercase(Locale.getDefault())
        val focusedLowerCase = focusedResultTitle.lowercase(Locale.getDefault())
        if (canShowHint &&
            inputLowerCase.isNotEmpty() &&
            focusedLowerCase.isNotEmpty() &&
            focusedLowerCase.matches(Regex("^[\\x00-\\x7F]*$")) &&
            focusedLowerCase.startsWith(inputLowerCase)
        ) {
            val hintColor = Themes.getAttrColor(context, android.R.attr.textColorTertiary)
            val hintText = SpannableStringBuilder(inputString)
                .append(focusedLowerCase.substring(inputLowerCase.length))
            hintText.setSpan(ForegroundColorSpan(Color.TRANSPARENT), 0, inputLowerCase.length, SPAN_POINT_MARK)
            hintText.setSpan(ForegroundColorSpan(hintColor), inputLowerCase.length, hintText.length, SPAN_POINT_MARK)
            hint.text = hintText
            hint.isVisible = true
        }
    }

    override fun onGlobalLayout() {
        canShowHint = input.layout?.getEllipsisCount(0) == 0
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        launcher.deviceProfile.inv.addOnChangeListener(this)
        if (::appsView.isInitialized) {
            appsView.appsStore?.addUpdateListener(this)
        }
        input.viewTreeObserver.addOnGlobalLayoutListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        launcher.deviceProfile.inv.removeOnChangeListener(this)
        if (::appsView.isInitialized) {
            appsView.appsStore?.removeUpdateListener(this)
        }
        input.viewTreeObserver.removeOnGlobalLayoutListener(this)
    }

    override fun onAppsUpdated() {
        searchBarController.refreshSearchResult()
    }

    override fun initializeSearch(appsView: ActivityAllAppsContainerView<*>) {
        apps = appsView.searchResultList as LawnchairAlphabeticalAppsList<*>
        mainApps = appsView.personalAppList as LawnchairAlphabeticalAppsList<*>
        this.appsView = appsView
        val algorithm = LawnchairSearchAlgorithm.create(context)
        this.searchAlgorithm = algorithm
        searchBarController.initialize(
            algorithm,
            input,
            launcher,
            this,
        )
        input.initialize(appsView)

        if (prefs2.enableDrawerPages.firstBlocking()) {
            showTabBar()
        }
    }

    override fun resetSearch() {
        searchBarController.reset()
    }

    override fun preDispatchKeyEvent(event: KeyEvent) {
        // Determine if the key event was actual text, if so, focus the search bar and then dispatch
        // the key normally so that it can process this key event
        if (!searchBarController.isSearchFieldFocused && event.action == KeyEvent.ACTION_DOWN) {
            val unicodeChar = event.unicodeChar
            val isKeyNotWhitespace = unicodeChar > 0 &&
                !Character.isWhitespace(unicodeChar) &&
                !Character.isSpaceChar(unicodeChar)
            if (isKeyNotWhitespace) {
                val gotKey = TextKeyListener.getInstance().onKeyDown(input, searchQueryBuilder, event.keyCode, event)
                if (gotKey && searchQueryBuilder.isNotEmpty()) {
                    searchBarController.focusSearchField()
                }
            }
        }
    }

    override fun onSearchResult(query: String, items: ArrayList<AdapterItem>?) {
        if (items != null) {
            apps.setSearchResults(items)
            notifyResultChanged()
            appsView.setSearchResults(items)
        }
    }

    override fun clearSearchResult() {
        if (apps.setSearchResults(null)) {
            notifyResultChanged()
        }

        // Clear the search query
        searchQueryBuilder.clear()
        searchQueryBuilder.clearSpans()
        Selection.setSelection(searchQueryBuilder, 0)
        appsView.onClearSearchResult()
        appsView.floatingHeaderView?.setFloatingRowsCollapsed(false)
    }

    private fun notifyResultChanged() {
        appsView.mSearchRecyclerView.onSearchResultsChanged()
    }

    override fun setInsets(insets: Rect) {
        (layoutParams as MarginLayoutParams).apply {
            topMargin = if (isInvisible) {
                insets.top - allAppsSearchVerticalOffset + drawerHandleAreaPx
            } else {
                max(-allAppsSearchVerticalOffset, insets.top - qsbMarginTopAdjusting) + drawerHandleAreaPx
            }
        }
        requestLayout()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        offsetTopAndBottom(allAppsSearchVerticalOffset)
    }

    override fun getEditText() = input

    override fun setBackgroundVisibility(visible: Boolean, maxAlpha: Float) {
        if (bgVisible != visible) {
            bgVisible = visible
            bgAlpha = maxAlpha
            if (visible) {
                bgAlphaAnimator.start()
            } else {
                bgAlphaAnimator.reverse()
            }
        } else if (bgAlpha != maxAlpha && !bgAlphaAnimator.isRunning && visible) {
            bgAlpha = maxAlpha
            bgAlphaAnimator.setCurrentFraction(maxAlpha)
            updateBgAlpha()
        }
    }

    override fun getBackgroundVisibility(): Boolean {
        return bgVisible
    }

    private fun updateBgAlpha() {
        val fraction = bgAlphaAnimator.animatedFraction
        bg.alpha = (Utilities.mapRange(fraction, 0f, bgAlpha) * 255).toInt()
    }

    override fun onIdpChanged(modelPropertiesChanged: Boolean) {
        setupPadding()
        invalidate()
        requestLayout()
    }
}
