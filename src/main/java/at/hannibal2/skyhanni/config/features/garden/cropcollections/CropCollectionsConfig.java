package at.hannibal2.skyhanni.config.features.garden.cropcollections;

import at.hannibal2.skyhanni.config.FeatureToggle;
import at.hannibal2.skyhanni.config.core.config.Position;
import com.google.gson.annotations.Expose;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDraggableList;
import io.github.notenoughupdates.moulconfig.annotations.ConfigLink;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static at.hannibal2.skyhanni.config.features.garden.cropcollections.CropCollectionsConfig.CropCollectionDisplayText.defaultDisplayList;

public class CropCollectionsConfig {
    @Expose
    @ConfigOption(
        name = "Collection Display",
        desc = "Show the progress and ETA until the next crop milestone is reached and the current crops/minute value.\n" +
            "§eRequires a tool with either a counter or Cultivating enchantment for full accuracy."
    )
    @ConfigEditorBoolean
    @FeatureToggle
    public boolean collectionDisplay = true;

    @Expose
    @ConfigOption(
        name = "Reset Session on game start",
        desc = "Resets session display mode when game starts."
    )
    @ConfigEditorBoolean
    @FeatureToggle
    public boolean resetSession = false;

    @Expose
    @ConfigLink(owner = CropCollectionsConfig.class, field = "collectionDisplay")
    public Position collectionDisplayPos = new Position(-400, -200, false, true);

    @Expose
    @ConfigOption(
        name = "Stats List",
        desc = "Drag text to change what displays in the summary card."
    )
    @ConfigEditorDraggableList
    public List<CropCollectionDisplayText> statDisplayList = new ArrayList<>(defaultDisplayList);

    public enum CropCollectionDisplayText {
        TITLE("Crop Collection"),
        ALL_TIME("All time"),
        TOTAL("Total"),
        SESSION("Session"),
        BREAKDOWN("Breakdown"),
        FARMING(" Farming"),
        BREAKING_CROPS("  Breaking Crops"),
        MOOSHROOM_COW("  Mooshroom Cow"),
        DICER("  Dicer Drops"),
        PESTS(" Pests"),
        PEST_RNG("  Pest Rng"),
        PEST_BASE("  Pest Base Drops"),
        UNKNOWN(" Unknown"),
        COLLECTION_PER_HOUR("Collection per hour")
        ;

        public static final List<CropCollectionDisplayText> defaultDisplayList = Arrays.asList(
            TITLE,
            TOTAL,
            SESSION,
            BREAKDOWN,
            FARMING,
            BREAKING_CROPS,
            MOOSHROOM_COW,
            DICER,
            PESTS,
            PEST_BASE,
            PEST_RNG,
            UNKNOWN,
            COLLECTION_PER_HOUR
        );

        private final String display;

        CropCollectionDisplayText(String display) {
            this.display = display;
        }

        @Override
        public String toString() {
            return display;
        }
    }
}
