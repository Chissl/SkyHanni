package at.hannibal2.skyhanni.features.garden.contest

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.ClickType
import at.hannibal2.skyhanni.events.garden.farming.CropClickEvent
import at.hannibal2.skyhanni.events.garden.farming.FarmingContestEvent
import at.hannibal2.skyhanni.features.garden.GardenApi
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.roundTo
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.TimeUtils.format

@SkyHanniModule
object JacobContestStatsSummary {

    private val config get() = GardenApi.config
    private var blocksBroken = 0
    private var startTime = SimpleTimeMark.farPast()

    @HandleEvent
    fun onCropClick(event: CropClickEvent) {
        if (!isEnabled()) return
        if (event.clickType != ClickType.LEFT_CLICK) return

        if (FarmingContestApi.inContest && event.crop == FarmingContestApi.contestCrop) {
            blocksBroken++
        }
    }

    @HandleEvent
    fun onFarmingContest(event: FarmingContestEvent) {
        if (!isEnabled()) return

        when (event.phase) {
            FarmingContestPhase.START -> {
                ChatUtils.chat("Started tracking your Jacob Contest Blocks Per Second!")
                startTime = SimpleTimeMark.now()
            }

            FarmingContestPhase.STOP -> {
                val duration = startTime.passedSince()
                val blocksPerSecond = (blocksBroken.toDouble() / duration.inWholeSeconds).roundTo(2)
                val cropName = event.crop.cropName
                ChatUtils.chat("Stats for $cropName Contest:")
                val time = duration.format()
                ChatUtils.chat("§7Blocks Broken in total: §e${blocksBroken.addSeparators()}")
                val color = getBlocksPerSecondColor(blocksPerSecond)
                ChatUtils.chat("§7Average Blocks Per Second: $color$blocksPerSecond")
                ChatUtils.chat("§7Participated for §b$time")
            }

            FarmingContestPhase.CHANGE -> {
                ChatUtils.chat("You changed the crop during the contest, resetting the Blocks Per Second calculation..")
                startTime = SimpleTimeMark.now()
            }
        }
        blocksBroken = 0
    }

    private fun getBlocksPerSecondColor(blocksPerSecond: Double) = if (blocksPerSecond > 19) "§c" else "§a"

    fun isEnabled() = GardenApi.inGarden() && config.jacobContestSummary
}
