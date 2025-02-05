package at.hannibal2.skyhanni.config.features.dungeon;

import at.hannibal2.skyhanni.config.FeatureToggle;
import at.hannibal2.skyhanni.config.core.config.Position;
import com.google.gson.annotations.Expose;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigLink;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

public class CreationCooldownConfig {
    @Expose
    @ConfigOption(
        name = "Enabled",
        desc = "Show the time until another dungeon instance can be created."
    )
    @ConfigEditorBoolean
    @FeatureToggle
    public boolean enabled = true;

    @Expose
    @ConfigOption(
        name = "Show Outside of Dungeons",
        desc = "Show on other skyblock islands."
    )
    @ConfigEditorBoolean
    public boolean showOutside = false;

    @Expose
    @ConfigOption(
        name = "Only Show In Entrance Room",
        desc = "Only show the display when in the entrance room of a dungeon."
    )
    @ConfigEditorBoolean
    public boolean entranceOnly = true;

    @Expose
    @ConfigOption(
        name = "Send Chat Message",
        desc = "Send a chat message when creation cooldown is over."
    )
    @ConfigEditorBoolean
    public boolean sendChatMessage = false;

    @Expose
    @ConfigOption(
        name = "Block Command Send",
        desc = "Block sending the /joininstance command during creation cooldown."
    )
    @ConfigEditorBoolean
    public boolean blockInstanceCreation = false;

    @Expose
    @ConfigLink(owner = CreationCooldownConfig.class, field = "enabled")
    public Position position = new Position(383, 93, false, true);
}
