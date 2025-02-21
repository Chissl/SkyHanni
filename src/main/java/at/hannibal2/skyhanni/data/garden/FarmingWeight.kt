package at.hannibal2.skyhanni.data.garden

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.HypixelData
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.data.garden.CropCollectionAPI.needCollectionUpdate
import at.hannibal2.skyhanni.data.garden.CropCollectionAPI.setCollectionCounter
import at.hannibal2.skyhanni.data.jsonobjects.other.ElitePlayerWeightJson
import at.hannibal2.skyhanni.data.jsonobjects.other.EliteWeightsJson
import at.hannibal2.skyhanni.events.IslandChangeEvent
import at.hannibal2.skyhanni.events.ProfileJoinEvent
import at.hannibal2.skyhanni.features.garden.CropType
import at.hannibal2.skyhanni.features.garden.GardenApi
import at.hannibal2.skyhanni.features.garden.pests.PestType
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.test.command.ErrorManager
import at.hannibal2.skyhanni.utils.ApiUtils
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.CollectionUtils.sumAllValues
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.LorenzUtils.isAnyOf
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.json.BaseGsonBuilder
import at.hannibal2.skyhanni.utils.json.SkyHanniTypeAdapters
import at.hannibal2.skyhanni.utils.json.fromJson
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.minutes

@SkyHanniModule
object FarmingWeight {

    @HandleEvent
    fun onProfileJoin(event: ProfileJoinEvent) {
        profileId = ""
        update()
    }

    @HandleEvent
    fun onIslandChange(event: IslandChangeEvent) {
        if (event.newIsland.isAnyOf(IslandType.GARDEN) || config.showOutsideGarden) {
            update()
        }
    }

    private val config get() = GardenApi.config.eliteFarmingWeights
    private var apiError = false

    private var lastUpdate = SimpleTimeMark.farPast()
    private var isLoadingWeight = AtomicBoolean(false)
    private var profileId = ""

    private val eliteWeightApiGson by lazy {
        BaseGsonBuilder.gson()
            .registerTypeAdapter(CropType::class.java, SkyHanniTypeAdapters.CROP_TYPE.nullSafe())
            .registerTypeAdapter(PestType::class.java, SkyHanniTypeAdapters.PEST_TYPE.nullSafe())
            .create()
    }

    fun isLoadingWeight() = isLoadingWeight.get()
    fun profileId() = profileId

    fun apiError() = apiError

    fun update() {
        getCropWeights()
        if (isLoadingWeight.compareAndSet(false, true)) {
            val localProfile = HypixelData.profileName
            apiError = false
            SkyHanniMod.coroutineScope.launch {
                loadWeight(localProfile)
                isLoadingWeight.set(false)
            }
        }
        getCropWeights()
    }

    private fun loadWeight(localProfile: String) {
        if (lastUpdate > SimpleTimeMark.now() - 15.minutes && !apiError) return
        val uuid = LorenzUtils.getPlayerUuid()
        val url = "https://api.elitebot.dev/weight/$uuid/?collections=True"
        val apiResponse = ApiUtils.getJSONResponse(url, apiName = "Elite Farming Weight")

        var error: Throwable? = null

        try {

            val apiData = eliteWeightApiGson.fromJson<ElitePlayerWeightJson>(apiResponse)

            val selectedProfileId = apiData.selectedProfileId
            var selectedProfileEntry = apiData.profiles.find { it.profileId == selectedProfileId }

            if (selectedProfileEntry == null || (selectedProfileEntry.profileName.lowercase() != localProfile && localProfile != "")) {
                selectedProfileEntry = apiData.profiles.find { it.profileName.lowercase() == localProfile }
            }
            if (selectedProfileEntry != null) {
                profileId = selectedProfileEntry.profileId
                val lastUpdated = selectedProfileEntry.lastUpdated
                if (lastUpdated >= CropCollectionAPI.lastGainedCollectionTime.toMillis() / 1000)
                    for (crop in selectedProfileEntry.crops) {
                        val cropType = CropType.getByName(crop.key)
                        cropType.setCollectionCounter(crop.value)
                        needCollectionUpdate = false
                    }

                GardenApi.storage?.farmingWeightUncountedCrops =
                    selectedProfileEntry.uncountedCrops.mapKeys { entry -> CropType.getByName(entry.key) }
                GardenApi.storage?.farmingWeightBonusWeight = selectedProfileEntry.bonusWeight.sumAllValues()
                ChatUtils.debug("Updated Crop Collection from Elite")
                lastUpdate = SimpleTimeMark.now()
                return
            }

        } catch (e: Exception) {
            error = e
        }
        apiError = true

        ErrorManager.logErrorWithData(
            error ?: IllegalStateException("Error loading user farming weight"),
            "Error loading user farming weight\n" +
                "§eLoading the farming weight data from elitebot.dev failed!\n" +
                "§eYou can re-enter the garden to try to fix the problem.\n" +
                "§cIf this message repeats, please report it on Discord",
            "url" to url,
            "apiResponse" to apiResponse,
            "localProfile" to localProfile,
        )
    }

    fun CropType.getFactor(): Double {
        return cropWeight[this] ?: backupCropWeights[this] ?: error("Crop $this not in backupFactors!")
    }

    private val cropWeight = mutableMapOf<CropType, Double>()
    private var attemptingCropWeightFetch = false
    private var hasFetchedCropWeights = false

    private fun getCropWeights() {
        if (attemptingCropWeightFetch || hasFetchedCropWeights) return
        attemptingCropWeightFetch = true
        val url = "https://api.elitebot.dev/weights/all"
        val apiResponse = ApiUtils.getJSONResponse(url, apiName = "Elite Farming Weight")

        try {
            val apiData = eliteWeightApiGson.fromJson<EliteWeightsJson>(apiResponse)
            apiData.crops
            for (crop in apiData.crops) {
                val cropType = CropType.getByNameOrNull(crop.key) ?: continue
                cropWeight[cropType] = crop.value
            }
            hasFetchedCropWeights = true
        } catch (e: Exception) {
            ErrorManager.logErrorWithData(
                e, "Error getting crop weights from elitebot.dev",
                "apiResponse" to apiResponse,
            )
        }
    }

    // TODO move to repo
    private val backupCropWeights by lazy {
        mapOf(
            CropType.WHEAT to 100_000.0,
            CropType.CARROT to 300_000.0,
            CropType.POTATO to 298_328.17,
            CropType.SUGAR_CANE to 198_85.45,
            CropType.NETHER_WART to 248_606.81,
            CropType.PUMPKIN to 99_236.12,
            CropType.MELON to 488_435.88,
            CropType.MUSHROOM to 90_944.27,
            CropType.COCOA_BEANS to 276_733.75,
            CropType.CACTUS to 178_730.65,
        )
    }
}

