package at.hannibal2.skyhanni.utils.tracker

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.config.core.config.Position
import at.hannibal2.skyhanni.config.storage.ProfileSpecificStorage
import at.hannibal2.skyhanni.data.ProfileStorageData
import at.hannibal2.skyhanni.data.RenderData
import at.hannibal2.skyhanni.data.SlayerApi
import at.hannibal2.skyhanni.data.TrackerManager
import at.hannibal2.skyhanni.features.misc.items.EstimatedItemValue
import at.hannibal2.skyhanni.test.command.ErrorManager
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.CollectionUtils
import at.hannibal2.skyhanni.utils.CollectionUtils.addSearchableSelector
import at.hannibal2.skyhanni.utils.ItemPriceSource
import at.hannibal2.skyhanni.utils.ItemPriceUtils.getPrice
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.RenderDisplayHelper
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.SearchTextInput
import at.hannibal2.skyhanni.utils.renderables.Searchable
import at.hannibal2.skyhanni.utils.renderables.buildSearchBox
import at.hannibal2.skyhanni.utils.renderables.toRenderable
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.client.gui.inventory.GuiInventory
import kotlin.time.Duration.Companion.seconds

@Suppress("TooManyFunctions")
open class SkyHanniTracker<Data : TrackerData>(
    val name: String,
    private val createNewSession: () -> Data,
    private val getStorage: (ProfileSpecificStorage) -> Data,
    vararg extraStorage: Pair<DisplayMode, (ProfileSpecificStorage) -> Data>,
    protected val drawDisplay: (Data) -> List<Searchable>,
) {

    protected val extraDisplayModes = extraStorage.toMap()
    protected var inventoryOpen = false
    internal var displayMode: DisplayMode? = null
    private val currentSessions = mutableMapOf<ProfileSpecificStorage, Data>()
    private var display = emptyList<Renderable>()
    private var sessionResetTime = SimpleTimeMark.farPast()
    private var wasSearchEnabled = config.trackerSearchEnabled.get()
    private var dirty = false
    protected val textInput = SearchTextInput()

    companion object {

        val config get() = SkyHanniMod.feature.misc.tracker
        internal val storedTrackers get() = SkyHanniMod.feature.storage.trackerDisplayModes

        fun getPricePer(name: NeuInternalName) = name.getPrice(config.priceSource)
    }

    fun isInventoryOpen() = inventoryOpen

    fun resetCommand() = ChatUtils.clickableChat(
        "Are you sure you want to reset your total $name? Click here to confirm.",
        onClick = {
            reset(DisplayMode.TOTAL, "Reset total $name!")
        },
        "§eClick to confirm.",
        oneTimeClick = true,
    )

    fun modify(modifyFunction: (Data) -> Unit) {
        getSharedTracker()?.let {
            it.modify(modifyFunction)
            update()
        }
    }

    fun modify(mode: DisplayMode, modifyFunction: (Data) -> Unit) {
        val sharedTracker = getSharedTracker() ?: return
        sharedTracker.modify(mode, modifyFunction)
        update()
    }

    private fun tryModify(mode: DisplayMode, modifyFunction: (Data) -> Unit) {
        getSharedTracker()?.let {
            it.tryModify(mode, modifyFunction)
            update()
        }
    }

    fun modifyEachMode(modifyFunction: (Data) -> Unit) {
        DisplayMode.entries.forEach {
            tryModify(it, modifyFunction)
        }
    }

    fun renderDisplay(position: Position) {
        if (config.hideInEstimatedItemValue && EstimatedItemValue.isCurrentlyShowing()) return

        var currentlyOpen = Minecraft.getMinecraft().currentScreen?.let { it is GuiInventory || it is GuiChest } ?: false
        if (RenderData.outsideInventory) {
            currentlyOpen = false
        }
        if (inventoryOpen != currentlyOpen) {
            inventoryOpen = currentlyOpen
            update()
        }

        val searchEnabled = config.trackerSearchEnabled.get()
        if (dirty || TrackerManager.dirty || (searchEnabled != wasSearchEnabled)) {
            display = getDisplay()
            dirty = false
        }
        wasSearchEnabled = searchEnabled

        position.renderRenderables(display, posLabel = name)
    }

    fun update() {
        dirty = true
    }

    protected open fun getDisplay() = getSharedTracker()?.let {
        val data = it.get(getDisplayMode())
        val searchables = drawDisplay(data)
        if (config.trackerSearchEnabled.get()) buildFinalDisplay(searchables.buildSearchBox(textInput))
        else buildFinalDisplay(Renderable.verticalContainer(searchables.toRenderable()))
    }.orEmpty()

    protected open fun buildFinalDisplay(searchBox: Renderable) = buildList {
        add(searchBox)
        if (isEmpty()) return@buildList
        if (inventoryOpen) {
            add(buildDisplayModeView())
            if (getDisplayMode() == DisplayMode.SESSION) {
                add(buildSessionResetButton())
            }
        }
    }

    protected fun buildSessionResetButton() = Renderable.clickAndHover(
        "§cReset session!",
        listOf(
            "§cThis will reset your",
            "§ccurrent session of",
            "§c$name",
        ),
        onClick = {
            if (sessionResetTime.passedSince() > 3.seconds) {
                reset(DisplayMode.SESSION, "Reset this session of $name!")
                sessionResetTime = SimpleTimeMark.now()
            }
        },
    )

    private val availableTrackers = arrayOf(DisplayMode.TOTAL, DisplayMode.SESSION) + extraDisplayModes.keys

    protected open fun buildDisplayModeView() = Renderable.horizontalContainer(
        CollectionUtils.buildSelector<DisplayMode>(
            "§7Display Mode: ",
            getName = { type -> type.displayName },
            isCurrent = { it == getDisplayMode() },
            onChange = {
                displayMode = it
                storedTrackers[name] = it
                update()
            },
            universe = availableTrackers,
        ),
    )

    protected open fun getSharedTracker() = ProfileStorageData.profileSpecific?.let { ps ->
        SharedTracker(
            mapOf(
                DisplayMode.TOTAL to ps.getTotal(),
                DisplayMode.SESSION to ps.getCurrentSession(),
            ) + extraDisplayModes.mapValues { it.value(ps) },
        )
    }

    private fun ProfileSpecificStorage.getCurrentSession() = currentSessions.getOrPut(this) { createNewSession() }

    private fun ProfileSpecificStorage.getTotal(): Data = getStorage(this)

    private fun reset(displayMode: DisplayMode, message: String) {
        getSharedTracker()?.let {
            it.get(displayMode).reset()
            ChatUtils.chat(message)
            update()
        }
    }

    protected fun getDisplayMode() = displayMode ?: run {
        val newValue = config.defaultDisplayMode.get().mode ?: storedTrackers[name] ?: DisplayMode.TOTAL
        displayMode = newValue
        newValue
    }

    fun firstUpdate() {
        if (display.isEmpty()) {
            update()
        }
    }

    fun initRenderer(position: Position, condition: () -> Boolean) {
        RenderDisplayHelper(
            outsideInventory = true,
            inOwnInventory = true,
            condition = condition,
            onRender = {
                renderDisplay(position)
            },
        )
    }

    inner class SharedTracker<Data : TrackerData>(
        private val entries: Map<DisplayMode, Data>,
    ) {

        fun modify(mode: DisplayMode, modifyFunction: (Data) -> Unit) {
            get(mode).let(modifyFunction)
        }

        fun tryModify(mode: DisplayMode, modifyFunction: (Data) -> Unit) {
            entries[mode]?.let(modifyFunction)
        }

        fun modify(modifyFunction: (Data) -> Unit) {
            entries.values.forEach(modifyFunction)
        }

        fun get(displayMode: DisplayMode) = entries[displayMode] ?: ErrorManager.skyHanniError(
            "Unregistered display mode accessed on tracker",
            "tracker" to name,
            "displayMode" to displayMode,
            "availableModes" to entries.keys,
        )
    }

    fun handlePossibleRareDrop(internalName: NeuInternalName, amount: Int) {
        val (itemName, price) = SlayerApi.getItemNameAndPrice(internalName, amount)
        if (config.warnings.chat && price >= config.warnings.minimumChat) {
            ChatUtils.chat("§a+Tracker Drop§7: §r$itemName")
        }
        if (config.warnings.title && price >= config.warnings.minimumTitle) {
            LorenzUtils.sendTitle("§a+ $itemName", 5.seconds)
        }
    }

    fun addPriceFromButton(lists: MutableList<Searchable>) {
        if (isInventoryOpen()) {
            lists.addSearchableSelector<ItemPriceSource>(
                "",
                getName = { type -> type.sellName },
                isCurrent = { it.ordinal == config.priceSource.ordinal }, // todo avoid ordinal
                onChange = { entry ->
                    config.priceSource = entry.let { ItemPriceSource.entries[entry.ordinal] } // todo avoid ordinal
                    update()
                },
            )
        }
    }

    enum class DisplayMode(val displayName: String) {
        TOTAL("Total"),
        SESSION("Session"),
        MAYOR("This Mayor"),
        DAY("Day"),
        WEEK("Week"),
        MONTH("Month"),
        YEAR("Year"),
    }

    enum class DefaultDisplayMode(val display: String, val mode: DisplayMode?) {
        TOTAL("Total", DisplayMode.TOTAL),
        SESSION("This Session", DisplayMode.SESSION),
        REMEMBER_LAST("Remember Last", null),
        ;

        override fun toString() = display
    }
}
