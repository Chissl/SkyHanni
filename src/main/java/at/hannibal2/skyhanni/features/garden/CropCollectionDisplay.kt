package at.hannibal2.skyhanni.features.garden

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.features.garden.cropcollections.CropCollectionsConfig.CropCollectionDisplayText
import at.hannibal2.skyhanni.data.garden.CropCollectionAPI
import at.hannibal2.skyhanni.data.garden.CropCollectionAPI.getCollectionCounter
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.data.garden.CropCollectionAPI.getTotalCropCollection
import at.hannibal2.skyhanni.data.garden.GardenCropMilestones
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.events.ConfigLoadEvent
import at.hannibal2.skyhanni.events.garden.farming.CropCollectionUpdateEvent
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.IslandChangeEvent
import at.hannibal2.skyhanni.events.ProfileJoinEvent
import at.hannibal2.skyhanni.features.garden.GardenAPI.addCropIcon
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.CollectionUtils.addString
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

@SkyHanniModule
object CropCollectionDisplay {
    private var collectionDisplay = emptyList<Renderable>()
    private val config get() = GardenAPI.config.cropCollections
    private var needsInventory = false

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
        update()
    }

    @HandleEvent(onlyOnIsland = IslandType.GARDEN)
    fun onGardenJoin(event: IslandChangeEvent) {
        update()
    }

    @HandleEvent
    fun onCollectionUpdate(event: CropCollectionUpdateEvent) {
        needsInventory = false
        update()
    }

    private fun update() {
        collectionDisplay = emptyList()
        val currentCrop = CropCollectionAPI.lastGainedCrop
        currentCrop?.let {
            collectionDisplay = drawCollectionDisplay(it)
        }
    }



    private fun drawCollectionDisplay(crop: CropType): List<Renderable> {
        val total = crop.getTotalCropCollection()
        val lineMap = mutableMapOf<CropCollectionDisplayText, Renderable>()
        lineMap[CropCollectionDisplayText.TITLE] = Renderable.string("§6Crop Collection")
        lineMap[CropCollectionDisplayText.CROP] = Renderable.horizontalContainer(
            buildList {
                addString("Crop: ")
                addCropIcon(crop)
                addString(crop.cropName)
            }
        )

        lineMap[CropCollectionDisplayText.TOTAL] = Renderable.string("Total: $total")

        lineMap[CropCollectionDisplayText.BREAKDOWN] = Renderable.string("Breakdown: ")

        val farming = crop.getCollectionCounter(CropCollectionType.MOOSHROOM_COW) +
            crop.getCollectionCounter(CropCollectionType.BREAKING_CROPS) +
            crop.getCollectionCounter(CropCollectionType.DICER
            )
        lineMap[CropCollectionDisplayText.FARMING] = Renderable.string(" Farming: ${farming.addSeparators()}")

        lineMap[CropCollectionDisplayText.BREAKING_CROPS] =
            Renderable.string("  Breaking Crops: ${crop.getCollectionCounter(CropCollectionType.BREAKING_CROPS).addSeparators()}")

        if (crop == CropType.MUSHROOM) lineMap[CropCollectionDisplayText.MOOSHROOM_COW] =
            Renderable.string("  Mooshroom Cow: ${crop.getCollectionCounter(CropCollectionType.MOOSHROOM_COW).addSeparators()}")

        lineMap[CropCollectionDisplayText.DICER] =
            Renderable.string("  Dicer Drops: ${crop.getCollectionCounter(CropCollectionType.DICER).addSeparators()}")

        val pests = crop.getCollectionCounter(CropCollectionType.PEST_BASE) + crop.getCollectionCounter(CropCollectionType.PEST_RNG)
        lineMap[CropCollectionDisplayText.PESTS] = Renderable.string(" Pests: ${pests.addSeparators()}")

        lineMap[CropCollectionDisplayText.PEST_BASE] =
            Renderable.string("  Pest Base Drops: ${crop.getCollectionCounter(CropCollectionType.PEST_BASE).addSeparators()}")

        lineMap[CropCollectionDisplayText.PEST_RNG] =
            Renderable.string("  Pest RNG: ${crop.getCollectionCounter(CropCollectionType.PEST_RNG).addSeparators()}")

        return formatDisplay(lineMap)
    }

    @SubscribeEvent
    fun onRenderOverlay(event: GuiRenderEvent.GuiOverlayRenderEvent) {
        if (!isEnabled()) return
        if (GardenAPI.hideExtraGuis()) return

        config.collectionDisplayPos.renderRenderables(
            collectionDisplay, posLabel = "Crop Collection"
        )
    }

    private fun formatDisplay(lineMap: MutableMap<CropCollectionDisplayText, Renderable>): List<Renderable> {
        val newList = mutableListOf<Renderable>()

        newList.addAll(config.statDisplayList.mapNotNull { lineMap[it] })
        newList.addString("Test Display!")
        return newList
    }

    private fun isEnabled() = config.collectionDisplay && GardenAPI.inGarden()
}
