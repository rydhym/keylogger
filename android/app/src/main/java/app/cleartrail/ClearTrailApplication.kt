package app.cleartrail

import android.app.Application
import java.util.concurrent.Executors

class ClearTrailApplication : Application() {
    val executor = Executors.newSingleThreadExecutor()
    // Accessed only on executor; survives Activity recreation, not process death.
    var controller: SessionController? = null
    fun engine(): SessionController = controller ?: SessionController(this).also { controller = it }
}
