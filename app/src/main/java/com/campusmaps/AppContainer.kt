package com.campusmaps

import android.app.Application
import android.content.Context
import com.campusmaps.data.AppClock
import com.campusmaps.data.DeviceIdProvider
import com.campusmaps.data.SettingsRepository
import com.campusmaps.data.campus.DemoBuildings
import com.campusmaps.data.model.Building
import com.campusmaps.data.shortcuts.FakeShortcutBackend
import com.campusmaps.data.shortcuts.PhotoStore
import com.campusmaps.data.shortcuts.ShortcutNotifier
import com.campusmaps.data.shortcuts.ShortcutRepository
import com.campusmaps.guidance.SimulatedGlassesLink
import com.campusmaps.platform.NetworkMonitor
import com.campusmaps.platform.Speaker
import com.campusmaps.platform.WatchBridge
import com.campusmaps.routing.Router
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// Builds every long lived object once and hands them out. No dependency injection library,
// just plain constructors, so it is easy to see what depends on what.
//
// To plug in real systems later, change the line here and nothing else:
//   buildings -> real building files
//   shortcutBackend -> HTTP client for the review queue
//   glasses -> the real glasses SDK
//   (positioning is created per route in GuidanceController)
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    // Lives as long as the app process. Used for syncing and the watch.
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val buildings: List<Building> = DemoBuildings.all
    val settings = SettingsRepository(appContext)
    val clock = AppClock()
    val router = Router()
    val speaker by lazy { Speaker(appContext) }
    val watch = WatchBridge(appContext, appScope)
    val network = NetworkMonitor(appContext, appScope)
    val submitterIdProvider = DeviceIdProvider(settings)
    val shortcutBackend = FakeShortcutBackend(isOnline = { network.online.value })
    val photos = PhotoStore(appContext)
    val shortcuts = ShortcutRepository(
        context = appContext,
        backend = shortcutBackend,
        submitterIdProvider = submitterIdProvider,
        online = network.online,
        notifier = ShortcutNotifier(appContext),
        scope = appScope,
    )
    val glasses by lazy { SimulatedGlassesLink(appScope, speaker) }

    fun building(id: String): Building = buildings.firstOrNull { it.id == id } ?: DemoBuildings.classroomSouth
}

class CampusMapsApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
