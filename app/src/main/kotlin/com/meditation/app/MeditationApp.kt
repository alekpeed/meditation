package com.meditation.app

import android.app.Application
import android.content.Context
import com.meditation.app.alarm.AlarmScheduler
import com.meditation.app.audio.AudioController
import com.meditation.app.data.ActiveSessionRepository
import com.meditation.app.data.HistoryRepository
import com.meditation.app.data.MeditationDatabase
import com.meditation.app.data.PreferencesRepository
import com.meditation.app.data.PresetRepository
import com.meditation.app.data.SoundRepository
import com.meditation.app.engine.AndroidClock
import com.meditation.app.engine.MeditationController
import com.meditation.app.service.NotificationController
import com.meditation.core.SessionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MeditationApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifications.ensureChannels()
        // Seed the bundled sound catalog and reconstruct any in-flight session after cold start.
        container.scope.launch {
            container.soundRepository.seedIfEmpty(this@MeditationApp)
        }
        container.controller.restore()
    }

    companion object {
        fun from(context: Context): MeditationApp = context.applicationContext as MeditationApp
    }
}

/**
 * Manual dependency container (a lightweight service locator). Keeps the single [MeditationController]
 * — the app's source of session truth — and all repositories as process-wide singletons without
 * pulling in a DI framework.
 */
class AppContainer(app: Application) {

    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val db = MeditationDatabase.get(app)

    val presetRepository = PresetRepository(db.presetDao())
    val activeSessionRepository = ActiveSessionRepository(db.activeSessionDao())
    val historyRepository = HistoryRepository(db.historyDao())
    val soundRepository = SoundRepository(db.soundDao(), db.attributionDao())
    val preferencesRepository = PreferencesRepository(app)

    val notifications = NotificationController(app)
    private val alarms = AlarmScheduler(app)
    val audio = AudioController(app, scope, soundRepository)

    val controller = MeditationController(
        appContext = app,
        scope = scope,
        engine = SessionEngine(AndroidClock),
        activeRepo = activeSessionRepository,
        historyRepo = historyRepository,
        prefs = preferencesRepository,
        audio = audio,
        alarms = alarms,
        notifications = notifications,
    )
}
