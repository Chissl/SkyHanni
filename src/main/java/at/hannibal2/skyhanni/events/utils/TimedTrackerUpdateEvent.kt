package at.hannibal2.skyhanni.events.utils

import at.hannibal2.skyhanni.api.event.SkyHanniEvent
import at.hannibal2.skyhanni.utils.tracker.SkyHanniTracker
import java.time.LocalDate

class TimedTrackerUpdateEvent(
    val trackerName: String,
    val displayMode: SkyHanniTracker.DisplayMode,
    val day: LocalDate,
    val week: LocalDate,
    val month: LocalDate,
    val year: LocalDate
) : SkyHanniEvent()
