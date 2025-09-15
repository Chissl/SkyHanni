package at.hannibal2.skyhanni.utils.tracker

import at.hannibal2.skyhanni.utils.Stopwatch
import com.google.gson.annotations.Expose

abstract class TrackerData {
    @Expose
    open var sessionUptime: Map<SessionUptime, Stopwatch> = mapOf(
        Pair(SessionUptime.Normal(NormalSession.NORMAL), Stopwatch())
    )

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
