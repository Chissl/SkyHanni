package at.hannibal2.skyhanni.features.itemabilities

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.IslandChangeEvent
import at.hannibal2.skyhanni.events.OwnInventorySlotEmptyEvent
import at.hannibal2.skyhanni.events.OwnInventoryItemUpdateEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.CollectionUtils.addItemStack
import at.hannibal2.skyhanni.utils.CollectionUtils.addString
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NeuInternalName.Companion.toInternalName
import at.hannibal2.skyhanni.utils.NeuItems.getItemStack
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.billion
import at.hannibal2.skyhanni.utils.NumberUtil.shortFormat
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SkyBlockItemModifierUtils.getCoinsOfAvarice
import at.hannibal2.skyhanni.utils.SkyBlockItemModifierUtils.getItemUuid
import at.hannibal2.skyhanni.utils.TimeUtils.format
import at.hannibal2.skyhanni.utils.inPartialHours
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.addLine
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object CrownOfAvariceCounter {

    private val config get() = SkyHanniMod.feature.inventory.itemAbilities.crownOfAvarice

    private val internalName = "CROWN_OF_AVARICE".toInternalName()

    private var display: List<Renderable> = emptyList()
    private var currentHelmet: String? = null
    private val MAX_AVARICE_COINS = 1.billion
    private val MAX_AFK_TIME = 2.minutes
    private val coinAmount: MutableMap<String, Long> = mutableMapOf()
    private var sessionResetTime = SimpleTimeMark.farPast()

    private var coinsEarned: Long = 0L
    private var sessionStart: SimpleTimeMark? = null
    private var lastCoinUpdate: SimpleTimeMark? = null
    private val isSessionActive get(): Boolean = sessionStart?.passedSince()?.let { it < config.sessionActiveTime.seconds } ?: false
    private var coinsDifference: Long? = null

    @HandleEvent
    fun onRenderOverlay(event: GuiRenderEvent) {
        if (!isEnabled()) return
        if (currentHelmet == null || display.isEmpty()) return
        config.position.renderRenderables(buildFinalDisplay(), posLabel = "Crown of Avarice Counter")
    }

    @HandleEvent
    fun onSecondPassed(event: SecondPassedEvent) {
        if (!isEnabled()) return
        if (currentHelmet == null) return
        update()
    }

    @HandleEvent
    fun onItemRemoved(event: OwnInventorySlotEmptyEvent) {
        if (!isEnabled() || event.slot != 5) return
        currentHelmet = null
        update()
    }

    @HandleEvent
    fun onInventoryUpdated(event: OwnInventoryItemUpdateEvent) {
        if (!isEnabled() || event.slot != 5) return
        val item = event.itemStack
        val uuid = item.getItemUuid()

        val coins = item.getCoinsOfAvarice()

        if (coins == null || uuid == null) {
            currentHelmet = null
            update()
            return
        } else {
            currentHelmet = uuid
        }

        if (coinAmount[uuid] == null) coinAmount[uuid] = coins

        coinsDifference = coins - (coinAmount[uuid] ?: 0)

        if (coinsDifference == 0L) return

        if ((coinsDifference ?: 0) < 0) {
            reset()
            coinAmount[uuid] = coins
            return
        }

        if (isSessionAFK()) reset()
        lastCoinUpdate = SimpleTimeMark.now()
        coinsEarned += coinsDifference ?: 0
        coinAmount[uuid] = coins

        update()
    }

    @HandleEvent
    fun onIslandChange(event: IslandChangeEvent) {
        reset()
    }

    private fun update() {
        display = buildList()
    }

    private fun buildList(): List<Renderable> = buildList {
        if (coinAmount[currentHelmet] == null) return@buildList
        addLine {
            addItemStack(internalName.getItemStack())
            addString(
                "§6" + if (config.shortFormat) coinAmount[currentHelmet]?.shortFormat() else coinAmount[currentHelmet]?.addSeparators()
            )
        }

        if (config.perHour) {
            val coinsPerHour = calculateCoinsPerHour().toLong()
            addString(
                "§aCoins Per Hour: §6${
                    if (isSessionActive) "Calculating..."
                    else if (config.shortFormatCPH) coinsPerHour.shortFormat() else coinsPerHour.addSeparators()
                } " + if (isSessionAFK()) "§c(AFK)" else "",
            )

        }
        if (config.time) {
            val timeUntilMax = calculateTimeUntilMax()
            addString(
                "§aTime until Max: §6${if (isSessionActive) "Calculating..." else timeUntilMax} " + if (isSessionAFK()) "§c(AFK)" else "",
            )
        }
        if (config.coinDiff) {
            addString("§aLast coins gained: §6${coinsDifference?.addSeparators()}")
        }
    }

    private fun buildFinalDisplay(): MutableList<Renderable> {
        val finalDisplay = display.toMutableList()
        if (InventoryUtils.inContainer()) {
            finalDisplay.add(buildResetButton())
        }
        return finalDisplay
    }

    private fun isEnabled() = LorenzUtils.inSkyBlock && config.enable

    private fun reset() {
        coinsEarned = 0L
        sessionStart = SimpleTimeMark.now()
        lastCoinUpdate = SimpleTimeMark.now()
        coinsDifference = 0L
        coinAmount.clear()

        setHelmet()
    }

    private fun calculateCoinsPerHour(): Double {
        val timeInHours = sessionStart?.passedSince()?.inPartialHours ?: 0.0
        return if (timeInHours > 0) coinsEarned / timeInHours else 0.0
    }

    private fun isSessionAFK() = lastCoinUpdate?.passedSince()?.let { it > MAX_AFK_TIME } ?: false

    private fun calculateTimeUntilMax(): String {
        val coinsPerHour = calculateCoinsPerHour()
        if (coinsPerHour == 0.0) return "Forever..."
        val timeUntilMax = ((MAX_AVARICE_COINS - (coinAmount[currentHelmet] ?: 0)) / coinsPerHour).hours
        return timeUntilMax.format()
    }

    private fun setHelmet() {
        val helmet = InventoryUtils.getHelmet() ?: return
        val coins = helmet.getCoinsOfAvarice()

        if (coins == null) {
            currentHelmet = null
            return
        }

        val uuid = helmet.getItemUuid() ?: return

        currentHelmet = uuid
        coinAmount[uuid] = coins
    }

    private fun buildResetButton() = Renderable.clickAndHover(
        "§cReset!",
        listOf(
            "§cThis will reset your",
            "§ccurrent session!",
        ),
        onClick = {
            if (sessionResetTime.passedSince() > 3.seconds) {
                reset()
                sessionResetTime = SimpleTimeMark.now()
                ChatUtils.chat("Reset Crown of Avarice Tracker!")
            }
        },
    )
}
