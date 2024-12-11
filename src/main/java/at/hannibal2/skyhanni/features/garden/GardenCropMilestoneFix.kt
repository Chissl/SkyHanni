package at.hannibal2.skyhanni.features.garden


import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern

@SkyHanniModule
object GardenCropMilestoneFix {
    private val patternGroup = RepoPattern.group("garden.cropmilestone.fix")


    /**
     * REGEX-TEST: §6§lRARE DROP! §9Mutant Nether Wart §6(§6+1,344☘)
     */
    private val pestRareDropPattern by patternGroup.pattern(
        "pests.raredrop",
        "§6§lRARE DROP! (?:§.)*(?<item>.+) §6\\(§6\\+.*☘\\)",
    )



    /*
        PestAPI.pestDeathChatPattern.matchMatcher(event.message) {
            val amount = group("amount").toInt()
            val item = NEUInternalName.fromItemNameOrNull(group("item")) ?: return

            val primitiveStack = NEUItems.getPrimitiveMultiplier(item)
            val rawName = primitiveStack.internalName.itemNameWithoutColor
            val cropType = CropType.getByNameOrNull(rawName) ?: return

            /*cropType.setMilestoneCounter(
                cropType.getMilestoneCounter() + (amount * primitiveStack.amount),
            )*/
            GardenCropMilestoneDisplay.update()
        }
        pestRareDropPattern.matchMatcher(event.message) {
            val item = NEUInternalName.fromItemNameOrNull(group("item")) ?: return

            val primitiveStack = NEUItems.getPrimitiveMultiplier(item)
            val rawName = primitiveStack.internalName.itemNameWithoutColor
            val cropType = CropType.getByNameOrNull(rawName) ?: return

            /*cropType.setMilestoneCounter(
                cropType.getMilestoneCounter() + primitiveStack.amount,
            )*/
            GardenCropMilestoneDisplay.update()
        }
    }*/







}
