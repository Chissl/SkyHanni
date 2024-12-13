package at.hannibal2.skyhanni.features.garden.farming

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.ClickType
import at.hannibal2.skyhanni.data.CropCollection.addCollectionCounter
import at.hannibal2.skyhanni.events.CropClickEvent
import at.hannibal2.skyhanni.events.GardenToolChangeEvent
import at.hannibal2.skyhanni.events.OwnInventoryItemUpdateEvent
import at.hannibal2.skyhanni.features.garden.CropCollectionType
import at.hannibal2.skyhanni.features.garden.CropType
import at.hannibal2.skyhanni.features.garden.GardenAPI
import at.hannibal2.skyhanni.features.garden.GardenAPI.getCropType
import at.hannibal2.skyhanni.features.garden.GardenAPI.readCounter
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.SkyBlockItemModifierUtils.getItemUuid
import net.minecraft.item.ItemStack
import kotlin.math.floor
import kotlin.random.Random

@SkyHanniModule
object GardenCropBreakTracker {
    private val storage get() = GardenAPI.storage
    private val counterData: MutableMap<String, Long>? get() = storage?.counterData
    private val blocksBroken: MutableMap<CropType, Long>? get() = storage?.blocksBroken

    private var cropBrokenType: CropType? = null
    private var heldItem: ItemStack? = null
    private var itemHasCounter: Boolean = false

    @HandleEvent
    fun onToolChange(event: GardenToolChangeEvent) {
        heldItem = event.toolItem ?: return
        val counter = readCounter(event.toolItem)

        itemHasCounter = (counter != -1L)
        if (!itemHasCounter) return

        val uuid = event.toolItem.getItemUuid() ?: return
        counterData?.set(uuid, counter)
    }

    @HandleEvent
    fun onCropBreak(event: CropClickEvent) {
        if (event.clickType != ClickType.LEFT_CLICK || !GardenAPI.inGarden()) return
        if (event.crop != cropBrokenType) cropBrokenType = event.crop

        blocksBroken?.set(event.crop, blocksBroken?.get(event.crop)?.plus(1) ?: 1)
        //Todo get by pet level
        if (GardenAPI.mushroomCowPet) {
            CropType.MUSHROOM.addCollectionCounter(
                CropCollectionType.MOOSHROOM_COW, weightedRandomRound(GardenAPI.mushroomCowPetLevel).toLong(), true
                )
            ChatUtils.debug(GardenAPI.mushroomCowPetLevel.toString())
        }

        if (itemHasCounter) return

        val fortune = storage?.latestTrueFarmingFortune?.get(event.crop) ?: return
        event.crop.addCollectionCounter(
            CropCollectionType.BREAKING_CROPS,
            ((weightedRandomRound(fortune%100) + floor(fortune/100) + 1) * 5.0).toLong(),
            true
        )
    }

    @HandleEvent
    fun onOwnInventoryItemUpdate(event: OwnInventoryItemUpdateEvent) {
        if (!GardenAPI.inGarden() || !itemHasCounter || event.itemStack.getItemUuid() != heldItem?.getItemUuid()) return
        val item = event.itemStack
        val uuid = item.getItemUuid() ?: return
        val counter = readCounter(item)
        val isHoe = GardenAPI.readHoeCounter(item) != null

        val crop = if (isHoe || cropBrokenType == null) event.itemStack.getCropType() else cropBrokenType
        if (crop == null) return

        val old = counterData?.get(uuid) ?: return
        val addedCounter = counter - old

        crop.addCollectionCounter(CropCollectionType.BREAKING_CROPS, addedCounter, true)
        counterData?.set(uuid, counter)
    }

    private fun weightedRandomRound(num: Double): Double {
        val randomNumber = Random.nextInt(1,100)
        return if (num >= randomNumber) 1.0 else 0.0
    }

    private val config get() = GardenAPI.config
}
