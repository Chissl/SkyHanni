package at.hannibal2.skyhanni.data

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.ConfigLoadEvent
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.ItemAddEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.ConditionalUtils
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NumberUtil.formatIntOrUserError

@SkyHanniModule
object TrackerManager {

    private var hasChanged = false
    var dirty = false
    var commandEditTrackerSuccess = false

    @HandleEvent
    fun onConfigLoad(event: ConfigLoadEvent) {
        val config = SkyHanniMod.feature.misc.tracker.hideCheapItems
        ConditionalUtils.onToggle(config.alwaysShowBest, config.minPrice, config.enabled) {
            hasChanged = true
        }
    }

    @HandleEvent(priority = HandleEvent.HIGHEST)
    fun onRenderOverlayFirst(event: GuiRenderEvent) {
        if (hasChanged) {
            dirty = true
        }
    }

    @HandleEvent(priority = HandleEvent.LOWEST)
    fun onRenderOverlayLast(event: GuiRenderEvent) {
        if (hasChanged) {
            dirty = false
            hasChanged = false
        }
    }

    fun commandEditTracker(args: Array<String>) {
        if (args.size < 2) {
            ChatUtils.userError("Usage: /shedittracker <item name> <amount>")
            return
        }

        val amount = args.last().formatIntOrUserError() ?: return
        if (amount == 0) {
            ChatUtils.userError("Amount can not be zero!")
            return
        }

        val rawName = args.dropLast(1).joinToString(" ")
        val internalName = NeuInternalName.fromItemNameOrInternalName(rawName)
        if (!internalName.isKnownItem()) {
            ChatUtils.chat("No item found for '$rawName'!")
            return
        }

        commandEditTrackerSuccess = false
        ItemAddEvent(internalName, amount, ItemAddManager.Source.COMMAND).post()
    }

    @HandleEvent(priority = HandleEvent.LOWEST)
    fun onItemAdd(event: ItemAddEvent) {
        if (event.source != ItemAddManager.Source.COMMAND || event.isCancelled) return
        if (!commandEditTrackerSuccess) {
            ChatUtils.userError("Could not edit the Item Tracker! Does this item belong to this tracker? Is the tracker active right now?")
        }
    }
}
