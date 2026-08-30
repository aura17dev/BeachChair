package app.lawnchair.search.algorithms

import android.content.Context
import android.os.Handler
import app.lawnchair.allapps.MostLaunchedTracker
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.search.adapter.SearchTargetFactory
import app.lawnchair.sexyspaces.SexySpacesBridge
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherModel
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ModelTaskController
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.search.SearchCallback
import com.android.launcher3.util.Executors
import com.patrykmichalik.opto.core.onEach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LawnchairAppSearchAlgorithm(context: Context) : LawnchairSearchAlgorithm(context) {

    private val appState = LauncherAppState.getInstance(context)
    private val resultHandler = Handler(Executors.MAIN_EXECUTOR.looper)

    // todo maybe use D.I.?
    private val searchTargetFactory = SearchTargetFactory(context)

    private var hiddenApps: Set<String> = setOf()

    private var hiddenAppsInSearch = ""
    private var enableFuzzySearch = false
    private var maxResultsCount = 5

    private val prefs2 = PreferenceManager2.getInstance(context)

    val coroutineScope = CoroutineScope(context = Dispatchers.IO)

    init {
        prefs2.enableFuzzySearch.onEach(launchIn = coroutineScope) {
            enableFuzzySearch = it
        }
        prefs2.hiddenApps.onEach(launchIn = coroutineScope) {
            hiddenApps = it
        }
        prefs2.hiddenAppsInSearch.onEach(launchIn = coroutineScope) {
            hiddenAppsInSearch = it
        }
        prefs2.maxAppSearchResultCount.onEach(launchIn = coroutineScope) {
            maxResultsCount = it
        }
    }

    override fun doSearch(query: String, callback: SearchCallback<BaseAllAppsAdapter.AdapterItem>) {
        appState.model.enqueueModelUpdateTask(object : LauncherModel.ModelUpdateTask {
            override fun execute(app: ModelTaskController, dataModel: BgDataModel, apps: AllAppsList) {
                coroutineScope.launch(Dispatchers.Main) {
                    val results = getResult(apps.data, query)
                    callback.onSearchResult(query, results)
                }
            }
        })
    }

    override fun cancel(interruptActiveRequests: Boolean) {
        if (interruptActiveRequests) {
            resultHandler.removeCallbacksAndMessages(null)
        }
    }

    private fun getResult(
        apps: MutableList<AppInfo>,
        query: String,
    ): ArrayList<BaseAllAppsAdapter.AdapterItem> {
        val tracker = MostLaunchedTracker.INSTANCE.get(context)
        val usageScore = { app: AppInfo -> tracker.currentScore(app.toComponentKey()) }
        val effectiveHiddenApps = hiddenApps + SexySpacesBridge.hiddenKeysFor(apps, context)
        val appResults = if (enableFuzzySearch) {
            SearchUtils.fuzzySearch(apps, query, maxResultsCount, effectiveHiddenApps, hiddenAppsInSearch, usageScore)
        } else {
            SearchUtils.normalSearch(apps, query, maxResultsCount, effectiveHiddenApps, hiddenAppsInSearch, usageScore)
        }

        // App launcher: every match is a big, full-width row. The first (best) match is
        // highlighted; tapping any row launches that app.
        val resultTargets = appResults.map { searchTargetFactory.createAppSearchTarget(it, bigRow = true) }
        setFirstItemQuickLaunch(resultTargets)

        val adapterItems = transformSearchResults(resultTargets)
        return ArrayList(adapterItems)
    }
}
