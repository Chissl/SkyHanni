package at.hannibal2.skyhanni.features.garden

import at.hannibal2.skyhanni.config.features.garden.cropmilestones.CropMilestonesConfig.MilestoneTextEntry
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.data.CropCollection.getTotalCropCollection
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule

@SkyHanniModule
object CropCollectionDisplay {
    private var collectionDisplay = emptyList<Renderable>()
    private val config get() = GardenAPI.config.cropCollections

    private fun drawCollectionDisplay(crop: CropType): List<Renderable> {
        val total = crop.getTotalCropCollection()
        return emptyList()
    }
}
