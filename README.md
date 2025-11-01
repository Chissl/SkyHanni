# WARNING: This is a work in progress! Make backups of your config before using this; going back to a normal build of skyhanni may cause partial or full config wipes!

Chissl's Branch of Skyhanni containing Garden Profit Tracker and related features. 

If you want to see these features polished and added to the regular version of skyhanni, please like my [pull request](https://github.com/hannibal002/SkyHanni/pull/4748)!

Make sure auto-updater is turned off.
Report any bugs not listed below that happen on this branch and not normal skyhanni to chissl on discord; suggestions are also appreciated!

**Do not use the 1.8.9 jar in the multi-version zip of the actions tab; workflows are broken and it will be corrupted.** 

## Features
- Garden Profit Tracker
- Visitor Drop Tracker
- Composter Tracker
- Crop Collection Tracker
- Tracker Uptime
- Elite Pests and Collection Leaderboard Displays
- Day, Week, Month, and Year modes for trackers
- Custom pest spawn sound
- Blocks broken/Bps tracker

## Fixes
- Elite farming weight should desync/error less
- Crop milestones include pest/dicer drops and should be a lot more accurate
- Fixed other various issues with crop milestones/farming weight

## Known issues
- Error messages regarding elite farming weight are sent when swapping lobbies/joining hypixel
    + Ignore these, they're wrong and shouldn't be sent at all
- Farming weight display overshoots because it doesn't subtract weight from pest kills
- Tracker uptime not properly pausing when active during a date change
- Confusing config options
    * If tracker uptime isn't showing up, disable "only show session" for that specific tracker"
    * I Implemented a config page for each tracker, this was not implemented well and I'm looking for better ways to do this
    * Confusing wording/descriptions in the garden profit tracker
    * Suggestions on how to better reword these are appreciated 
- Bits/Copper not respecting npc prices
- Elite leaderboard displays do not respect visibility settings
- Some non-essential data like crop milestones is not correctly migrated over 
    * Do /cropmilestones to fix
- Pest waypoint is disabled
