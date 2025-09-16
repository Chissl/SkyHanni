package at.hannibal2.skyhanni.utils.tracker

import at.hannibal2.skyhanni.utils.Stopwatch
import com.google.gson.annotations.Expose
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

abstract class TrackerData {
    @Expose
    internal open var sessionUptime: Map<SessionUptime, Stopwatch> = mapOf(
        Pair(SessionUptime.Normal(NormalSession.NORMAL), Stopwatch())
    )

    private var _activeSession: SessionUptime? = null

    // avoid initializing until we need it since subclass overrides sessionUptime
    private var activeSession: SessionUptime?
        get() {
            if (_activeSession == null) {
                _activeSession = sessionUptime.keys.firstOrNull()
            }
            return _activeSession
        }
        private set(value) {
            _activeSession = value
        }

    fun getActiveStopwatch(): Stopwatch? = activeSession?.let { sessionUptime[it] }

    fun setActiveStopwatch(session: SessionUptime) {
        require(sessionUptime.containsKey(session)) {
            "Session $session not part of this tracker"
        }
        if (session == activeSession) return
        val duration = sessionUptime[activeSession]?.pause(revertLap = true)
        activeSession = session
        sessionUptime[activeSession]?.start()
        sessionUptime[activeSession]?.add(duration ?: 0.seconds)
    }

    open fun getTotalUptime(): Duration =
        sessionUptime.values.fold(Duration.ZERO) { acc, stopwatch ->
            acc + stopwatch.getDuration()
        }

    fun reset() {
        for (session in sessionUptime.entries) {
            sessionUptime[session.key]?.reset()
        }
        resetData()
    }

    protected abstract fun resetData()
}

abstract class GardenTrackerData : TrackerData() {
    @Expose
    override var sessionUptime: Map<SessionUptime, Stopwatch> = mapOf(
        Pair(SessionUptime.Garden(GardenSession.CROP), Stopwatch()),
        Pair(SessionUptime.Garden(GardenSession.PEST), Stopwatch()),
        Pair(SessionUptime.Garden(GardenSession.VISITOR), Stopwatch())
    )
}

sealed class SessionUptime {
    data class Normal(val sessionType: NormalSession) : SessionUptime()
    data class Garden(val sessionType: GardenSession) : SessionUptime()
}

enum class NormalSession {
    NORMAL,
}

enum class GardenSession {
    PEST,
    VISITOR,
    CROP,
}
