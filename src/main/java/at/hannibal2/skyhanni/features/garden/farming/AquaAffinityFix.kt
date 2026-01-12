package at.hannibal2.skyhanni.features.garden.farming

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.events.DebugDataCollectEvent
import at.hannibal2.skyhanni.features.garden.GardenApi
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.SkyBlockItemModifierUtils.getHypixelEnchantments
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EquipmentSlotGroup
import net.minecraft.world.entity.ai.attributes.AttributeInstance
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes

@SkyHanniModule
object AquaAffinityFix {
    private val config get() = GardenApi.config
    private val rl: ResourceLocation =
        ResourceLocation.withDefaultNamespace("enchantment.aqua_affinity").withSuffix("/" + EquipmentSlotGroup.HEAD.serializedName)
    private val am: AttributeModifier = AttributeModifier(rl, 4.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)

    @HandleEvent(onlyOnIsland = IslandType.GARDEN)
    fun onTick() {
        val hasAquaAffinity = hasAquaAffinity()
        val attr = getSubMiningAttr() ?: return

        if (hasAquaAffinity && config.aquaAffinityFix.get()) {
            if (attr.value == .2) {
                attr.addTransientModifier(am)
            }
        } else {
            attr.removeModifier(am)
        }
    }

    private fun getSubMiningAttr(): AttributeInstance? {
        val player = getPlayer() ?: return null
        return player.getAttribute(Attributes.SUBMERGED_MINING_SPEED)
    }

    private fun getPlayer(): LocalPlayer? {
        return Minecraft.getInstance().player
    }

    private fun hasAquaAffinity(): Boolean {
        return InventoryUtils.getHelmet()?.getHypixelEnchantments()?.get("aqua_affinity") != null
    }

    @HandleEvent
    fun onDebug(event: DebugDataCollectEvent) {
        event.title("Aqua Affinity")
        event.addIrrelevant {
            addAll(
                buildList {
                    val hasAquaAffinity = hasAquaAffinity()
                    val attr = getSubMiningAttr()
                    add("Has Aqua Affinity: $hasAquaAffinity")
                    add("Submerged Mining Speed: ${attr?.value}")
                    add("Enchantments: ${InventoryUtils.getHelmet()?.getHypixelEnchantments()?.keys}")
                },
            )
        }
    }

}
