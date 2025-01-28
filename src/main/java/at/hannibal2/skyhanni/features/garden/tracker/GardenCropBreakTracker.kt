package at.hannibal2.skyhanni.features.garden.tracker

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.ClickType
import at.hannibal2.skyhanni.data.garden.CropCollectionAPI.addCollectionCounter
import at.hannibal2.skyhanni.events.GardenToolChangeEvent
import at.hannibal2.skyhanni.events.OwnInventoryItemUpdateEvent
import at.hannibal2.skyhanni.events.garden.farming.CropClickEvent
import at.hannibal2.skyhanni.features.garden.CropCollectionType
import at.hannibal2.skyhanni.features.garden.CropType
import at.hannibal2.skyhanni.features.garden.GardenApi
import at.hannibal2.skyhanni.features.garden.GardenApi.getCropType
import at.hannibal2.skyhanni.features.garden.GardenApi.readCounter
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.SkyBlockItemModifierUtils.getItemUuid
import net.minecraft.item.ItemStack
import kotlin.math.floor
import kotlin.random.Random

@SkyHanniModule
object GardenCropBreakTracker {
    private val storage get() = GardenApi.storage
    private val counterData: MutableMap<String, Long>? get() = storage?.counterData
    private val blocksBroken: MutableMap<CropType, Long>? get() = storage?.blocksBroken

    private var cropBrokenType: CropType? = null
    private var heldItem: ItemStack? = null
    private var itemHasCounter: Boolean = false

    @HandleEvent
    fun onToolChange(event: GardenToolChangeEvent) {
        heldItem = event.toolItem
        if (event.toolItem == null || event.toolInHand == null) return
        val counter = readCounter(event.toolItem) ?: return

        val uuid = event.toolItem.getItemUuid() ?: return
        counterData?.set(uuid, counter)
    }

    @HandleEvent
    fun onCropBreak(event: CropClickEvent) {
        if (event.clickType != ClickType.LEFT_CLICK || !GardenApi.inGarden()) return
        if (event.crop != cropBrokenType) cropBrokenType = event.crop

        blocksBroken?.set(event.crop, blocksBroken?.get(event.crop)?.plus(1) ?: 1)
        // TODO via pet api
        if (GardenApi.mushroomCowPet) {
            CropType.MUSHROOM.addCollectionCounter(
                CropCollectionType.MOOSHROOM_COW, weightedRandomRound(GardenApi.mushroomCowPetLevel / 100.0).toLong()
            )
        }

        if (itemHasCounter || heldItem == null) return

        val fortune = storage?.latestTrueFarmingFortune?.get(event.crop) ?: return
        event.crop.addCollectionCounter(
            CropCollectionType.BREAKING_CROPS,
            ((weightedRandomRound(fortune % 100) + floor(fortune / 100) + 1) * 5.0).toLong()
        )
    }

    // TODO account for late updates after swapping held item
    @HandleEvent
    fun onOwnInventoryItemUpdate(event: OwnInventoryItemUpdateEvent) {
        if (!GardenApi.inGarden() || !itemHasCounter || event.itemStack.getItemUuid() != heldItem?.getItemUuid()) return
        val item = event.itemStack
        val uuid = item.getItemUuid() ?: return
        val counter = readCounter(item) ?: return
        val isHoe = GardenApi.readHoeCounter(item) != null

        val crop = if (isHoe || cropBrokenType == null) event.itemStack.getCropType() else cropBrokenType
        if (crop == null) return

        val old = counterData?.get(uuid) ?: return
        val addedCounter = counter - old

        crop.addCollectionCounter(CropCollectionType.BREAKING_CROPS, addedCounter)
        counterData?.set(uuid, counter)
    }

    private fun weightedRandomRound(num: Double): Double {
        val randomNumber = Random.nextInt(1, 100)
        return if (num >= randomNumber) 1.0 else 0.0
    }

    private val config get() = GardenApi.config
}
