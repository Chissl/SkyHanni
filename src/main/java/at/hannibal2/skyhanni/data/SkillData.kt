package at.hannibal2.skyhanni.data

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.api.SkillApi.storage
import at.hannibal2.skyhanni.data.jsonobjects.repo.neu.NeuSkillLevelJson
import at.hannibal2.skyhanni.data.model.TabWidget
import at.hannibal2.skyhanni.events.ActionBarUpdateEvent
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.events.NeuRepositoryReloadEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.events.SkillExpGainEvent
import at.hannibal2.skyhanni.events.SkillOverflowLevelUpEvent
import at.hannibal2.skyhanni.features.skillprogress.SkillProgress
import at.hannibal2.skyhanni.features.skillprogress.SkillType
import at.hannibal2.skyhanni.features.skillprogress.SkillUtil.SPACE_SPLITTER
import at.hannibal2.skyhanni.features.skillprogress.SkillUtil.calculateLevelXP
import at.hannibal2.skyhanni.features.skillprogress.SkillUtil.calculateSkillLevel
import at.hannibal2.skyhanni.features.skillprogress.SkillUtil.getLevelExact
import at.hannibal2.skyhanni.features.skillprogress.SkillUtil.getSkillInfo
import at.hannibal2.skyhanni.features.skillprogress.SkillUtil.xpRequiredForLevel
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ItemUtils.cleanName
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.NumberUtil.formatDouble
import at.hannibal2.skyhanni.utils.NumberUtil.formatLong
import at.hannibal2.skyhanni.utils.NumberUtil.romanToDecimalIfNecessary
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import com.google.gson.annotations.Expose
import java.util.LinkedList
import java.util.regex.Matcher
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object SkillApi {
    private val patternGroup = RepoPattern.group("api.skilldisplay")

    /* Action Bar */
    /**
     * REGEX-TEST: +1.1 Mining (48.39%)
     */
    private val skillPercentPattern by patternGroup.pattern(
        "skill.percent",
        "\\+(?<gained>[\\d.,]+) (?<skillName>.+) \\((?<progress>[\\d.,]+)%\\)",
    )

    /**
     * REGEX-TEST: +6.3 Foraging (24/750)
     */
    private val skillMultiplierPattern by patternGroup.pattern(
        "skill.multiplier",
        "\\+(?<gained>[\\d.,]+) (?<skillName>.+) \\((?<current>[\\d.,]+)\\/(?<needed>[\\d,.]+[kmb]?)\\)",
    )

    /* Tablist */
    // TODO find out whats going on here
    /**
     * REGEX-TEST: Farming 35: §r§a12.4%
     */
    private val skillTabPattern by patternGroup.pattern(
        "skill.tab",
        " (?<type>\\w+)(?: (?<level>\\d+))?: §r§a(?<progress>[0-9.]+)%",
    )

    // TODO add regex tests
    private val maxSkillTabPattern by patternGroup.pattern(
        "skill.tab.max",
        " (?<type>\\w+) (?<level>\\d+): §r§c§lMAX",
    )

    // TODO add regex tests
    private val skillTabNoPercentPattern by patternGroup.pattern(
        "skill.tab.nopercent",
        " §r§a(?<type>\\w+)(?: (?<level>\\d+))?: §r§e(?<current>[0-9,.]+)§r§6/§r§e(?<needed>[0-9kmb]+)",
    )

    private var skillXPInfoMap = mutableMapOf<SkillType, SkillXPInfo>()
    private var oldSkillInfoMap = mutableMapOf<SkillType?, SkillInfo?>()
    private var exactLevelingMap = mapOf<Int, Int>()
    private var levelingMap = mapOf<Int, Int>()
    private var levelArray = listOf<Int>()
    private var activeSkill: SkillType? = null

    private var showDisplay = false
    private var lastUpdate = SimpleTimeMark.farPast()

    // TODO handle this with stopwatches
    @HandleEvent(SecondPassedEvent::class)
    fun onSecondPassed() {
        val activeSkill = activeSkill ?: return
        val info = skillXPInfoMap[activeSkill] ?: return
        if (!info.sessionTimerActive) return

        val time = when (activeSkill) {
            SkillType.FARMING -> SkillProgress.etaConfig.farmingPauseTime
            SkillType.MINING -> SkillProgress.etaConfig.miningPauseTime
            SkillType.COMBAT -> SkillProgress.etaConfig.combatPauseTime
            SkillType.FORAGING -> SkillProgress.etaConfig.foragingPauseTime
            SkillType.FISHING -> SkillProgress.etaConfig.fishingPauseTime
            else -> 0
        }
        if (info.lastUpdate.passedSince() > time.seconds) {
            info.sessionTimerActive = false
        }
        if (info.sessionTimerActive) {
            info.timeActive++
        }
    }

    @HandleEvent
    fun onActionBarUpdate(event: ActionBarUpdateEvent) {
        val actionBar = event.actionBar.removeColor()
        val components = SPACE_SPLITTER.splitToList(actionBar)
        for (component in components) {
            val matcher = listOf(skillPercentPattern, skillMultiplierPattern).firstOrNull {
                it.matcher(component).matches()
            }?.matcher(component)

            if (matcher?.matches() != true) continue
            val skillName = matcher.group("skillName")
            val skillType = SkillType.getByNameOrNull(skillName) ?: return
            val skillInfo = storage?.get(skillType) ?: SkillInfo()
            val skillXP = skillXPInfoMap[skillType] ?: SkillXPInfo()
            activeSkill = skillType
            when (matcher.pattern()) {
                skillPercentPattern -> handleSkillPatternPercent(matcher, skillType)
                skillMultiplierPattern -> handleSkillPatternMultiplier(matcher, skillType, skillInfo)
            }

            showDisplay = true
            lastUpdate = SimpleTimeMark.now()
            skillXP.lastUpdate = SimpleTimeMark.now()
            skillXP.sessionTimerActive = true
            SkillProgress.updateDisplay()
            SkillProgress.hideInActionBar = listOf(component)
            return
        }
    }

    @HandleEvent
    fun onNEURepoReload(event: NeuRepositoryReloadEvent) {
        val data = event.getConstant<NeuSkillLevelJson>("leveling")

        levelArray = data.levelingXP
        levelingMap = levelArray.withIndex().associate { (index, xp) -> (index + 1) to xp }
        exactLevelingMap = levelArray.withIndex().associate { (index, xp) -> xp to (index + 1) }
    }

    @HandleEvent
    fun onInventoryFullyOpened(event: InventoryFullyOpenedEvent) {
        if (event.inventoryName != "Your Skills") return
        for (stack in event.inventoryItems.values) {
            val lore = stack.getLore()
            if (lore.none { it.contains("Click to view!") || it.contains("Not unlocked!") }) continue
            val cleanName = stack.cleanName()
            val split = cleanName.split(" ")
            val skillName = split.first()
            val skill = SkillType.getByNameOrNull(skillName) ?: continue
            val skillLevel = if (split.size > 1) split.last().romanToDecimalIfNecessary() else 0
            val skillInfo = storage?.getOrPut(skill, SkillApi::SkillInfo) ?: continue

            lore@ for ((index, line) in lore.withIndex()) {
                val cleanLine = line.removeColor()
                if (!cleanLine.startsWith("                    ")) continue@lore
                val previousLine = lore.getOrNull(index - 1) ?: continue@lore
                val progress = cleanLine.substring(cleanLine.lastIndexOf(' ') + 1)
                if (previousLine == "§7§8Max Skill level reached!") {
                    onUpdateMax(progress, skill, skillInfo, skillLevel)
                } else {
                    onUpdateNotMax(progress, skillLevel, skillInfo)
                }
            }
        }
    }

    /* Handle skill inventory updates */

    private fun onUpdateMax(progress: String, skill: SkillType, skillInfo: SkillInfo, skillLevel: Int) {
        val totalXP = progress.formatLong()
        val cap = skill.maxLevel
        val maxXP = xpRequiredForLevel(cap)
        val currentXP = totalXP - maxXP
        val (overflowLevel, overflowCurrent, overflowNeeded, overflowTotal) = calculateSkillLevel(totalXP, cap)

        skillInfo.update(overflowCurrent, overflowNeeded, overflowTotal, overflowLevel, currentXP, 0L, totalXP, skillLevel)
    }

    private fun onUpdateNotMax(progress: String, skillLevel: Int, skillInfo: SkillInfo) {
        val splitProgress = progress.split("/")
        val currentXP = splitProgress.first().formatLong()
        val neededXP = splitProgress.last().formatLong()
        val levelXP = calculateLevelXP(skillLevel - 1).toLong()

        val totalXP = levelXP + currentXP
        skillInfo.update(currentXP, neededXP, totalXP, skillLevel, currentXP, neededXP, totalXP, skillLevel)
    }

    /* Handle Action Bar Updates */

    private fun handleSkillPatternPercent(matcher: Matcher, skillType: SkillType) {
        var current = 0L
        var needed = 0L
        var xpPercentage = 0.0
        var isPercentPatternFound = false
        var tablistLevel: Int? = null

        for (line in TabWidget.SKILLS.lines) {
            skillTabPattern.matchMatcher(line) {
                if (group("type") == skillType.displayName) {
                    tablistLevel = group("level").toInt()
                    isPercentPatternFound = true
                    if (group("type").lowercase() != activeSkill?.lowercaseName) tablistLevel = null
                }
            }

            maxSkillTabPattern.matchMatcher(line) {
                if (group("type") == skillType.displayName) {
                    tablistLevel = group("level").toInt()
                    if (group("type").lowercase() != activeSkill?.lowercaseName) tablistLevel = null
                }
            }

            skillTabNoPercentPattern.matchMatcher(line) {
                if (group("type") == skillType.displayName) {
                    tablistLevel = group("level").toInt()
                    current = group("current").formatLong()
                    needed = group("needed").formatLong()
                    isPercentPatternFound = false
                    return@matchMatcher
                }
            }
            xpPercentage = matcher.group("progress").formatDouble()
        }

        val existingLevel = getSkillInfo(skillType) ?: SkillInfo()
        val level = tablistLevel ?: return
        if (isPercentPatternFound) {
            val levelXP = calculateLevelXP(existingLevel.level - 1)
            val nextLevelDiff = levelArray.getOrNull(level)?.toDouble() ?: 7_600_000.0
            val nextLevelProgress = nextLevelDiff * xpPercentage / 100
            val totalXP = levelXP + nextLevelProgress
            updateSkillInfo(
                skillType,
                existingLevel,
                level,
                nextLevelProgress.toLong(),
                nextLevelDiff.toLong(),
                totalXP.toLong(),
                matcher.group("gained"),
            )
        } else {
            val exactLevel = getLevelExact(needed)
            val levelXP = calculateLevelXP(existingLevel.level - 1).toLong() + current
            updateSkillInfo(skillType, existingLevel, exactLevel, current, needed, levelXP, matcher.group("gained"))
        }
        storage?.set(skillType, existingLevel)
    }

    private fun updateSkillInfo(
        skillType: SkillType,
        existingLevel: SkillInfo,
        level: Int,
        currentXP: Long,
        maxXP: Long,
        totalXP: Long,
        gained: String
    ) {
        val cap = activeSkill?.maxLevel
        val add = cap?.takeIf { level >= it }?.let {
            xpRequiredForLevel(it)
        } ?: 0

        val diff = totalXP - existingLevel.totalXp
        if (diff > 0) SkillExpGainEvent(skillType, diff.toDouble())

        val (levelOverflow, currentOverflow, currentMaxOverflow, totalOverflow) =
            calculateSkillLevel(totalXP + add, cap ?: 60)

        existingLevel.update(currentOverflow, currentMaxOverflow, totalOverflow, levelOverflow, currentXP, maxXP, totalXP, level, gained)
    }

    private fun handleSkillPatternMultiplier(matcher: Matcher, skillType: SkillType, skillInfo: SkillInfo) {
        val currentXP = matcher.group("current").formatLong()
        val maxXP = matcher.group("needed").formatLong()

        // when at overflow, we dont need to subtract one level in the logic below
        val minus = if (maxXP == 0L) 0 else 1
        val level = getLevelExact(maxXP) - minus

        val levelXP = calculateLevelXP(level - 1).toLong() + currentXP
        val (currentLevel, currentOverflow, currentMaxOverflow, totalOverflow) =
            calculateSkillLevel(levelXP, skillType.maxLevel)

        if (skillInfo.overflowLevel > skillType.maxLevel && currentLevel == skillInfo.overflowLevel + 1) {
            SkillOverflowLevelUpEvent(skillType, skillInfo.overflowLevel, currentLevel).post()
        }

        val diff = totalOverflow - skillInfo.overflowTotalXp
        if (diff > 0) SkillExpGainEvent(skillType, diff.toDouble())

        skillInfo.update(
            currentOverflow,
            currentMaxOverflow,
            totalOverflow,
            currentLevel,
            currentXP,
            maxXP,
            levelXP,
            level,
            matcher.group("gained")
        )

        storage?.set(skillType, skillInfo)
    }

    data class SkillInfo(
        // TODO rename all Xp -> XP
        @Expose var level: Int = 0,
        @Expose var totalXp: Long = 0,
        @Expose var currentXp: Long = 0,
        @Expose var currentXpMax: Long = 0,
        @Expose var overflowLevel: Int = 0,
        @Expose var overflowCurrentXp: Long = 0,
        @Expose var overflowTotalXp: Long = 0,
        @Expose var overflowCurrentXpMax: Long = 0,
        @Expose var lastGain: String = "",
        @Expose var customGoalLevel: Int = 0,
    ) {
        fun update(
            currentOverflow: Long,
            currentMaxOverflow: Long,
            totalOverflow: Long,
            overflowLevel: Int,
            currentXP: Long,
            maxXP: Long,
            totalXP: Long,
            level: Int,
            lastGain: String? = null
        ) {
            this.overflowCurrentXp = currentOverflow
            this.overflowCurrentXpMax = currentMaxOverflow
            this.overflowTotalXp = totalOverflow
            this.overflowLevel = overflowLevel

            this.currentXp = currentXP
            this.currentXpMax = maxXP
            this.totalXp = totalXP
            this.level = level

            if (lastGain != null) this.lastGain = lastGain
        }
    }

    data class SkillXPInfo(
        var lastTotalXP: Float = 0f,
        var xpGainQueue: LinkedList<Float> = LinkedList(),
        var xpGainHour: Float = 0f,
        var xpGainLast: Float = 0f,
        var timer: Int = 3,
        var sessionTimerActive: Boolean = false,
        var isActive: Boolean = false,
        var lastUpdate: SimpleTimeMark = SimpleTimeMark.farPast(),
        var timeActive: Long = 0L,
    )
}
