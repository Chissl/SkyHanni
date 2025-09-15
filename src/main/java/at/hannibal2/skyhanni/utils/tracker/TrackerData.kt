package at.hannibal2.skyhanni.utils.tracker

import at.hannibal2.skyhanni.utils.Stopwatch
import com.google.gson.annotations.Expose
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

abstract class TrackerData {
    @Expose
    internal open var sessionUptime: Map<SessionUptime, Stopwatch> = mapOf(
        Pair(SessionUptime.Normal(NormalSession.NORMAL), Stopwatch())
    )

    private var activeSession: SessionUptime? = sessionUptime.keys.firstOrNull()

    fun getActiveSession(): SessionUptime? = activeSession

    fun getActiveStopwatch(): Stopwatch? = activeSession?.let { sessionUptime[it] }

    fun setActiveSession(session: SessionUptime) {
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

abstract class GardenTrackerData: TrackerData() {
    @Expose
    override var sessionUptime: Map<SessionUptime, Stopwatch> = mapOf(
        Pair(SessionUptime.Garden(GardenSession.CROP), Stopwatch()),
        Pair(SessionUptime.Garden(GardenSession.PEST), Stopwatch()),
        Pair(SessionUptime.Garden(GardenSession.VISITOR), Stopwatch())
    )
}

sealed class SessionUptime {
    data class Normal(val sessionType: NormalSession): SessionUptime()
    data class Garden(val sessionType: GardenSession): SessionUptime()
}

enum class NormalSession(private val displayName: String) {
    NORMAL("Normal"),
    ;
}

enum class GardenSession(private val displayName: String) {
    PEST("Pest"),
    VISITOR("Visitor"),
    CROP("Crop", ),
}
