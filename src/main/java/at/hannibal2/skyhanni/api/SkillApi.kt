package at.hannibal2.skyhanni.api

import at.hannibal2.skyhanni.data.ProfileStorageData

object SkillApi {
    val storage get() = ProfileStorageData.profileSpecific?.skillData
}
