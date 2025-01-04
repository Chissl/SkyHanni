package at.hannibal2.skyhanni.features.garden

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.features.garden.cropcollections.CropCollectionsConfig.CropCollectionDisplayText
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.data.garden.CropCollectionAPI
import at.hannibal2.skyhanni.data.garden.CropCollectionAPI.getTotalCropCollection
import at.hannibal2.skyhanni.data.garden.GardenCropMilestones
import at.hannibal2.skyhanni.events.ConfigLoadEvent
import at.hannibal2.skyhanni.events.DateChangeEvent
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.IslandChangeEvent
import at.hannibal2.skyhanni.events.ProfileJoinEvent
import at.hannibal2.skyhanni.events.garden.farming.CropCollectionAddEvent
import at.hannibal2.skyhanni.events.garden.farming.CropCollectionUpdateEvent
import at.hannibal2.skyhanni.features.garden.GardenAPI.addCropIcon
import at.hannibal2.skyhanni.features.garden.GardenAPI.storage
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.CollectionUtils.addString
import at.hannibal2.skyhanni.utils.CollectionUtils.sumAllValues
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.inPartialHours
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.Searchable
import at.hannibal2.skyhanni.utils.renderables.toSearchable
import at.hannibal2.skyhanni.utils.tracker.SkyHanniTracker
import at.hannibal2.skyhanni.utils.tracker.SkyhanniTimedTracker
import at.hannibal2.skyhanni.utils.tracker.TimedTrackerData
import at.hannibal2.skyhanni.utils.tracker.TrackerData
import com.google.gson.annotations.Expose
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.util.EnumMap
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object CropCollectionDisplay {
    private val config get() = GardenAPI.config.cropCollections
    private var needsInventory = false
    private val tracker = SkyhanniTimedTracker<Data>(
        "Crop Collection Tracker",
        { Data() },
        { it.garden.cropCollectionTracker },
        { drawDisplay(it) }
    )

    class TimeData : TimedTrackerData<Data>({ Data() }) {
        init {
            if (config.resetSession) {
                getOrPutEntry(SkyHanniTracker.DisplayMode.SESSION).reset()
            }
        }
    }

    class Data : TrackerData() {
        override fun reset() {
            cropCollection.clear()
        }

        @Expose
        var cropCollection: MutableMap<CropType, CropCollection> = EnumMap(CropType::class.java)
    }

    class CropCollection {
        fun getTotal(): Long {
            return cropCollectionType.sumAllValues().toLong()
        }

        fun getCollection(collectionType: CropCollectionType): Long {
            return cropCollectionType.getOrPut(collectionType) { 0 }
        }

        @Expose
        var cropCollectionType: MutableMap<CropCollectionType, Long> = EnumMap(CropCollectionType::class.java)
    }

    @HandleEvent(priority = HandleEvent.LOW)
    fun onProfileJoin(event: ProfileJoinEvent) {
        GardenCropMilestones.cropMilestoneCounter?.let {
            if (it.values.sum() == 0L) {
                needsInventory = true
            }
        }
    }

    @HandleEvent
    fun onConfigLoad(event: ConfigLoadEvent) {
        tracker.update()
    }

    @HandleEvent(onlyOnIsland = IslandType.GARDEN)
    fun onGardenJoin(event: IslandChangeEvent) {
        tracker.update()
    }

    @HandleEvent
    fun onCollectionUpdate(event: CropCollectionUpdateEvent) {
        needsInventory = false
        tracker.update()
    }

    @HandleEvent
    fun onCollectionAdd(event: CropCollectionAddEvent) {
        val crop = event.crop
        val type = event.cropCollectionType
        val amount = event.amount
        if (type == CropCollectionType.UNKNOWN || amount <= 0) return
        tracker.modify {
            val cropType = it.cropCollection.getOrPut(crop) { CropCollection() }
            cropType.cropCollectionType[type] = cropType.cropCollectionType.getOrDefault(type, 0) + amount
        }
    }

    @HandleEvent
    fun onDateChange(event: DateChangeEvent) {
        tracker.changeDate(event.oldDate, event.newDate)
    }


    // Todo add bucketed tracker
    private fun drawDisplay(data: Data): List<Searchable> {
        val crop = CropCollectionAPI.lastGainedCrop ?: return emptyList()
        val allTime = crop.getTotalCropCollection()
        val cropData = data.cropCollection.getOrPut(crop) { CropCollection() }
        val lineMap = mutableMapOf<CropCollectionDisplayText, Searchable>()
        val displayMode = tracker.displayMode ?: return emptyList()
        val date = when (displayMode) {
            SkyHanniTracker.DisplayMode.WEEK -> tracker.week
            SkyHanniTracker.DisplayMode.MONTH -> tracker.month
            SkyHanniTracker.DisplayMode.YEAR -> tracker.year
            else -> tracker.date
        }

        lineMap[CropCollectionDisplayText.TITLE] = Renderable.horizontalContainer(
            buildList {
                addCropIcon(crop)
                addString("§7")
                addString(crop.cropName)
                addString(" §6Collection")
            }
        ).toSearchable()

        lineMap[CropCollectionDisplayText.ALL_TIME] = Renderable.string("§7All-Time: §e${allTime.addSeparators()}").toSearchable()

        val total: Long = cropData.getTotal()
        lineMap[CropCollectionDisplayText.TOTAL] = Renderable.string("§7Total: §e${total.addSeparators()}").toSearchable()

        lineMap[CropCollectionDisplayText.BREAKDOWN] = Renderable.string("§6Breakdown: ").toSearchable()

        val farming = cropData.getCollection(CropCollectionType.MOOSHROOM_COW) +
            cropData.getCollection(CropCollectionType.BREAKING_CROPS) +
            cropData.getCollection(CropCollectionType.DICER)

        lineMap[CropCollectionDisplayText.FARMING] = Renderable.string(" §7Farming: §e${farming.addSeparators()}").toSearchable()

        lineMap[CropCollectionDisplayText.BREAKING_CROPS] =
            Renderable.string(
                "  §7Breaking Crops: §e${cropData.getCollection(CropCollectionType.BREAKING_CROPS).addSeparators()}"
            ).toSearchable()

        if (crop == CropType.MUSHROOM) lineMap[CropCollectionDisplayText.MOOSHROOM_COW] =
            Renderable.string(
                "  §7Mooshroom Cow: §e${cropData.getCollection(CropCollectionType.MOOSHROOM_COW).addSeparators()}"
            ).toSearchable()

        lineMap[CropCollectionDisplayText.DICER] =
            Renderable.string(
                "  §7Dicer Drops: §e${cropData.getCollection(CropCollectionType.DICER).addSeparators()}"
            ).toSearchable()

        val pests = cropData.getCollection(CropCollectionType.PEST_BASE) + cropData.getCollection(CropCollectionType.PEST_RNG)
        lineMap[CropCollectionDisplayText.PESTS] = Renderable.string(" §2Pests: §e${pests.addSeparators()}").toSearchable()

        lineMap[CropCollectionDisplayText.PEST_BASE] =
            Renderable.string(
                "  §2Pest Base Drops: §e${cropData.getCollection(CropCollectionType.PEST_BASE).addSeparators()}"
            ).toSearchable()

        lineMap[CropCollectionDisplayText.PEST_RNG] =
            Renderable.string(
                "  §2Pest RNG: §e${cropData.getCollection(CropCollectionType.PEST_RNG).addSeparators()}"
            ).toSearchable()

        val uptimeData = storage?.uptimeTracker?.getEntry(displayMode, date)

        // Todo respect config
        if (uptimeData != null) {
            val uptime = uptimeData.pestTime + uptimeData.visitorTime + uptimeData.cropBreakTime
            if (uptime != 0) {
                val collectionPerHour = total / uptime.seconds.inPartialHours
                lineMap[CropCollectionDisplayText.SESSION] =
                    Renderable.string("§7Per hour: ${collectionPerHour.toLong().addSeparators()}").toSearchable()
            }
        }

        return formatDisplay(lineMap)
    }

    @SubscribeEvent
    fun onRenderOverlay(event: GuiRenderEvent) {
        if (!isEnabled()) return
        if (GardenAPI.hideExtraGuis()) return

        tracker.renderDisplay(config.collectionDisplayPos)
    }

    private fun formatDisplay(lineMap: MutableMap<CropCollectionDisplayText, Searchable>): List<Searchable> {
        val newList = mutableListOf<Searchable>()

        newList.addAll(config.statDisplayList.mapNotNull { lineMap[it] })
        return newList
    }

    private fun isEnabled() = config.collectionDisplay && GardenAPI.inGarden()
}
