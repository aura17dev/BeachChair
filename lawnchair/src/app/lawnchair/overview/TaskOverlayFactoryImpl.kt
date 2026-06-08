package app.lawnchair.overview

import android.content.Context
import androidx.annotation.Keep
import com.android.quickstep.TaskOverlayFactory
import com.android.quickstep.views.TaskContainer
import com.android.systemui.shared.recents.model.Task

@Keep
class TaskOverlayFactoryImpl(@Suppress("UNUSED_PARAMETER") context: Context) : TaskOverlayFactory() {

    override fun createOverlay(thumbnailView: TaskContainer) = TaskOverlay(thumbnailView)

    class TaskOverlay(
        taskThumbnailView: TaskContainer,
    ) : TaskOverlayFactory.TaskOverlay<LawnchairOverviewActionsView>(taskThumbnailView) {
        // initOverlay is intentionally empty here; overlay population is handled by the
        // parent TaskOverlayFactory. The Go variant override no longer applies post-merge.
    }

    sealed interface OverlayUICallbacks : TaskOverlayFactory.OverlayUICallbacks {
        fun onShare()
        fun onLens()
        fun onLocked(context: Context, task: Task)
    }
}
