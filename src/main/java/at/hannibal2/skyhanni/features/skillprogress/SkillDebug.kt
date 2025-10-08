package at.hannibal2.skyhanni.features.skillprogress

import at.hannibal2.skyhanni.data.SkillApi.SkillInfo
import at.hannibal2.skyhanni.api.SkillApi.activeSkill
import at.hannibal2.skyhanni.api.SkillApi.storage
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.DebugDataCollectEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule

@SkyHanniModule
object SkillDebug {
    @HandleEvent
    fun onDebug(event: DebugDataCollectEvent) {
        event.title("Skills")
        val storage = storage
        if (storage == null) {
            event.addIrrelevant("SkillMap is empty")
            return
        }

        event.addIrrelevant {
            val activeSkill = activeSkill
            if (activeSkill == null) {
                add("activeSkill is null")
            } else {
                add("active skill:")
                storage[activeSkill]?.let { skillInfo ->
                    addDebug(activeSkill, skillInfo)
                }
                add("")
                add("")
            }

            for ((skillType, skillInfo) in storage) {
                addDebug(skillType, skillInfo)
            }
        }
    }

    private fun MutableList<String>.addDebug(skillType: SkillType, skillInfo: SkillInfo) {
        add("Name: $skillType")
        add("-  Level: ${skillInfo.level}")
        add("-  CurrentXp: ${skillInfo.currentXp}")
        add("-  CurrentXpMax: ${skillInfo.currentXpMax}")
        add("-  TotalXp: ${skillInfo.totalXp}")
        add("-  OverflowLevel: ${skillInfo.overflowLevel}")
        add("-  OverflowCurrentXp: ${skillInfo.overflowCurrentXp}")
        add("-  OverflowCurrentXpMax: ${skillInfo.overflowCurrentXpMax}")
        add("-  OverflowTotalXp: ${skillInfo.overflowTotalXp}")
        add("-  CustomGoalLevel: ${skillInfo.customGoalLevel}\n")
    }
}
