package app.lawnchair

import kotlinx.coroutines.Dispatchers

/**
 * App-wide coroutine dispatcher constants. Capping icon and DB threads at a low limit prevents
 * icon-pack loading or Room queries from monopolising the shared IO pool (default size = 64).
 */
object LauncherDispatchers {
    val iconIO = Dispatchers.IO.limitedParallelism(4)
    val dbIO = Dispatchers.IO.limitedParallelism(2)
}
