package at.hannibal2.skyhanni.data.garden

import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.events.garden.farming.CropCollectionAddEvent
import at.hannibal2.skyhanni.events.garden.farming.CropCollectionUpdateEvent
import at.hannibal2.skyhanni.features.garden.CropCollectionType
import at.hannibal2.skyhanni.features.garden.CropType
import at.hannibal2.skyhanni.features.garden.GardenAPI
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NumberUtil.formatLong
import at.hannibal2.skyhanni.utils.RegexUtils.firstMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matchGroup
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.StringUtils.cleanPlayerName
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
// Todo: since we're using tracker, we don't need crop collection type
@SkyHanniModule
object CropCollectionAPI {

    private val patternGroup = RepoPattern.group("data.garden.collection")

    /**
     * REGEX-TEST: §b[MVP§5+§b] Chissl§7: §e24.7B
     */
    private val coopCounterPattern by patternGroup.pattern(
        "counter.coop",
        "(?<playerName>.*)§7: §e(?<amount>[\\d.BKM]+)",
    )

    /**
     * REGEX-TEST: §7Total collected: §e5,470,236,124
     */
    private val soloCounterPattern by patternGroup.pattern(
        "counter.solo",
        "§7Total collected: §e(?<amount>[\\d,]+)",
    )

    /**
     * REGEX-TEST: §eMelon IX
     */
    private val collectionNamePattern by patternGroup.pattern(
        "name",
        "§e(?<name>.*) [XIV]{1,3}",
    )

    private val storage get() = GardenAPI.storage

    private val cropCollectionCounter:
        MutableMap<CropType, MutableMap<CropCollectionType, Long>>? get() = GardenAPI.storage?.cropCollectionCounter

    var lastGainedCrop: CropType?
        get() = GardenAPI.storage?.lastGainedCrop
        set(value) {
            value?.let {
                GardenAPI.storage?.lastGainedCrop = it
            }
        }

    var lastGainedCollectionTime = SimpleTimeMark.farPast()

    var needCollectionUpdate = true

    fun CropType.getTotalCropCollection() =
        cropCollectionCounter?.get(this)?.values?.sum() ?: 0L

    fun CropType.getCollectionCounter(collectionType: CropCollectionType) =
        cropCollectionCounter?.get(this)?.get(collectionType) ?: 0L

    private fun CropType.setCollectionCounter(
        collectionType: CropCollectionType,
        counter: Long
    ) {
        val crop = cropCollectionCounter?.get(this)
        if (crop != null) {
            crop[collectionType] = counter
        } else {
            val innerMap = mutableMapOf<CropCollectionType, Long>()
            innerMap[collectionType] = counter
            cropCollectionCounter?.set(this, innerMap)
        }
        CropCollectionUpdateEvent.post()
    }

    // TODO make compatible with crop milestone fixes
    fun CropType.addCollectionCounter(type: CropCollectionType, amount: Long) {
        if (amount == 0L) return
        if (type != CropCollectionType.UNKNOWN && amount > 1) lastGainedCrop = this

        this.setCollectionCounter(type, amount + this.getCollectionCounter(type))

        lastGainedCollectionTime = SimpleTimeMark.now()
        CropCollectionAddEvent(this, type, amount).post()
    }

    @SubscribeEvent
    fun onInventoryOpen(event: InventoryFullyOpenedEvent) {
        val inventoryName = event.inventoryName
        if (inventoryName.endsWith("Farming Collections")) {
            for (stack in event.inventoryItems.values) {
                val name = collectionNamePattern.matchGroup(stack.displayName, "name") ?: continue
                if (name == "Seeds") continue
                val crop = CropType.getByNameOrNull(name) ?: continue
                val oldAmount = crop.getTotalCropCollection()

                soloCounterPattern.firstMatcher(stack.getLore()) {
                    val amount = group("amount").formatLong()
                    val change = amount - oldAmount

                    crop.addCollectionCounter(CropCollectionType.UNKNOWN, change)
                    storage?.lastCollectionFix?.set(crop, SimpleTimeMark.now())
                    needCollectionUpdate = false
                }
                for (line in stack.getLore()) {
                    coopCounterPattern.matchMatcher(line) {
                        val playerName = group("playerName").cleanPlayerName()
                        val amount = group("amount")

                        if (playerName != LorenzUtils.getPlayerName()) return@matchMatcher
                        ChatUtils.debug("Name: $name Crop: $crop Player: $playerName Amount: $amount")

                        val amountLong = when {
                            amount.endsWith("K") -> amount.removeSuffix("K").toFloat() * 1000
                            amount.endsWith("M") -> amount.removeSuffix("M").toFloat() * 1000000
                            amount.endsWith("B") -> amount.removeSuffix("B").toFloat() * 1000000000
                            else -> amount.toFloat()
                        }.toLong()

                        val amountString = amountLong.toString()
                        val oldAmountString = oldAmount.toString()
                        if (amountLong > oldAmount ||
                            amountString.length != oldAmountString.length || amountString[0] != oldAmountString[0]
                        ) {
                            crop.setCollectionCounter(CropCollectionType.UNKNOWN, amountLong)
                            needCollectionUpdate = true
                        }
                    }
                }
            }
        }
    }
}
