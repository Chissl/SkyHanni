package at.hannibal2.skyhanni.features.skillprogress

import at.hannibal2.skyhanni.data.SkillApi.storage
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.commands.CommandCategory
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.features.skillprogress.SkillUtil.calculateSkillLevel
import at.hannibal2.skyhanni.features.skillprogress.SkillUtil.xpRequiredForLevel
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.formatLongOrUserError
import at.hannibal2.skyhanni.utils.StringUtils

@SkyHanniModule
object SkillCommand {
    @Suppress("ReturnCount")
    private fun onCommand(it: Array<String>) {
        if (it.isEmpty()) {
            commandHelp()
            return
        }

        val first = it.first()
        if (it.size == 1) {
            when (first) {
                "goal" -> {
                    ChatUtils.chat("§bSkill Custom Goal Level")
                    val map = storage?.filter { it.value.customGoalLevel != 0 } ?: return
                    if (map.isEmpty()) {
                        ChatUtils.userError("You haven't set any custom goals yet!")
                    }
                    map.forEach { (skill, data) ->
                        ChatUtils.chat("§e${skill.displayName}: §b${data.customGoalLevel}")
                    }
                    return
                }
            }
        }

        if (it.size == 2) {
            val second = it[1]
            when (first) {
                "levelwithxp" -> {
                    val xp = second.formatLongOrUserError() ?: return
                    val (overflowLevel, current, needed, _) = calculateSkillLevel(xp, 60)
                    ChatUtils.chat(
                        "With §b${xp.addSeparators()} §eXP you would be level §b$overflowLevel " +
                            "§ewith progress (§b${current.addSeparators()}§e/§b${needed.addSeparators()}§e) XP",
                    )
                    return
                }

                "xpforlevel" -> {
                    val level = second.toIntOrNull()
                    if (level == null) {
                        ChatUtils.userError("Not a valid number: '$second'")
                        return
                    }
                    val neededXP = xpRequiredForLevel(level)
                    ChatUtils.chat("You need §b${neededXP.addSeparators()} §eXP to reach level §b${level.toDouble()}")
                    return
                }

                "goal" -> {
                    val rawSkill = it[1].lowercase()
                    val skillType = SkillType.getByNameOrNull(rawSkill)
                    if (skillType == null) {
                        ChatUtils.userError("Unknown Skill type: $rawSkill")
                        return
                    }
                    val skill = storage?.get(skillType) ?: return
                    skill.customGoalLevel = 0
                    ChatUtils.chat("Custom goal level for §b${skillType.displayName} §ereset")
                    return
                }
            }
        }
        if (it.size == 3) {
            when (first) {
                "goal" -> {
                    val rawSkill = it[1].lowercase()
                    val skillType = SkillType.getByNameOrNull(rawSkill)
                    if (skillType == null) {
                        ChatUtils.userError("Unknown Skill type: $rawSkill")
                        return
                    }
                    val rawLevel = it[2]
                    val targetLevel = rawLevel.toIntOrNull()
                    if (targetLevel == null) {
                        ChatUtils.userError("$rawLevel is not a valid number.")
                        return
                    }
                    val skill = storage?.get(skillType) ?: return

                    if (targetLevel <= skill.overflowLevel) {
                        ChatUtils.userError(
                            "Custom goal level ($targetLevel) must be greater than your current level (${skill.overflowLevel}).",
                        )
                        return
                    }

                    skill.customGoalLevel = targetLevel
                    ChatUtils.chat("Custom goal level for §b${skillType.displayName} §eset to §b$targetLevel")
                    return
                }
            }
        }
        commandHelp()
    }

    private fun commandHelp() {
        ChatUtils.chat(
            listOf(
                "§6/shskills levelwithxp <xp> - §bGet a level with the given current XP.",
                "§6/shskills xpforlevel <desiredLevel> - §bGet how much XP you need for a desired level.",
                "§6/shskills goal - §bView your current goal",
                "§6/shskills goal <skill> <level> - §bDefine your goal for <skill>",
                "",
            ).joinToString("\n"),
            prefix = false,
        )
    }

    @HandleEvent
    fun onCommandRegistration(event: CommandRegistrationEvent) {
        event.register("shskills") {
            description = "Skills XP/Level related command"
            category = CommandCategory.USERS_ACTIVE
            callback { onCommand(it) }
            autoComplete { args ->
                when (args.size) {
                    1 -> listOf("levelwithxp", "xpforlevel", "goal")
                    2 -> if (args[0].lowercase() == "goal") StringUtils.getListOfStringsMatchingLastWord(
                        args,
                        SkillType.entries.map { it.displayName },
                    ) else listOf()

                    else -> listOf()
                }
            }
        }
    }
}
