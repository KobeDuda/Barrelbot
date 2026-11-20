# Barrelbot classroom for Paper 26.3

## Install

Get the classroom JAR and both pack ZIPs from the same v16 release or workflow artifact. To build them from this checkout, follow the [build instructions](RELEASE.md). The Maryville reset-day world and WorldGuard regions file come from the classroom operator’s archive.

Use Java 25, Paper 26.3, WorldGuard, and WorldEdit or FastAsyncWorldEdit. The classroom integration was exercised with Paper build 256, WorldGuard 7.0.17 and FAWE 2.16.0.

1. Put `BarrelbotClassroom-26.3.jar` in the server’s `plugins` folder. Put `barrelbot_data_pack_v16.zip` directly in the **primary world’s** `datapacks` folder. Install one version of the Barrelbot datapack.
2. Give every client `barrelbot_resource_pack_v16.zip`. For a shared server, host the ZIP at a URL the learners’ clients can reach, set `resource-pack` and `resource-pack-sha1` in `server.properties`, and enable `require-resource-pack`. The SHA-1 is the hash of the ZIP itself.
3. For the Maryville classroom, use a copy of **Maryville reset-day1-2026**. With the server stopped, install the supplied `regions-barrelbot-26.yml` as `plugins/WorldGuard/worlds/<world folder>/regions.yml`. Give instructors `barrelbot.instructor` through your permissions plugin. Operators already have that permission.
4. Start the server and use `/datapack list enabled` and `/bb`. Open **Choose a lesson by name**, select **puzzle3-loops**, then inspect lane 1. On a world imported without its original scoreboard, the inspector shows an unassigned ID. During setup, use **Import unassigned classroom puzzles**. Review the targets and conflicts, then apply. The import rebuilds puzzle IDs and restores the instruction inventories from the authored source boxes.
5. Use lane 2 with a learner account before opening the classroom. A useful check is to take an instruction across the boundary, then run a dispenser bot and recall its instructions.

WorldGuard supplies the live lane and lesson geometry. The plugin includes the supplied Maryville coordinates for instructor previews when no live regions are available. Additional world folder names can opt into those coordinates through the `maryville-worlds` list in the plugin config. Learner building privileges use live WorldGuard permissions. The archived Bukkit aliases for teleports, inventory clearing, ranks and quests belong to their respective server plugins. Barrelbot row actions work independently of those aliases.

## During a lesson

Sneak-right-click the start button or yellow source box to inspect a puzzle. The yellow outline shows its volume. The menu and selection belong to the player who opened them. `/bb select` also selects the puzzle under the crosshair.

- **Build.** In a permitted creative build space, open **Blocks and instructions**. It supplies the start button, bot, dispenser bot, source box, function block, and instruction items. Place the start button and use **Set first boundary corner**, then look at the opposite block and click **Set opposite corner**. **Puzzle features** includes win conditions, bot conversion and linking a held item to a function block.
- **Configure instructions.** The builder places the intended stacks and arguments in the source box and clicks **Lock box contents**. The receipt identifies the puzzle, box, quantities and configured loop or condition. `/bb list` shows the saved starting set. When a puzzle has multiple boxes, look at the one to lock.
- **Test together.** The builder and assigned partner can use **Return instructions to box**. It stops the run, restores the saved quantities, and invalidates older managed copies, including items a player brings back after reconnecting. The partner can use the instructions and return them. The builder controls the locked set. A full source box leaves an undelivered stack with its carrier.
- **Assign partners.** New puzzles remember their builder. Instructors can use `/bb builder <online account>` on a selected puzzle. The builder or instructor can use `/bb partner <online account>`. For existing puzzles with no explicit assignment, the plugin reads numbered lane memberships and the adjacent lane pair from WorldGuard. Shared build lanes with several members can use an explicit builder assignment.
- **Configurable items.** `/bb give counter 5` gives a five-cycle counter. `/bb give while ahead air` and `/bb give if below lime_concrete` accept a block and one of `ahead`, `behind`, `left`, `right`, `above`, or `below`.

## During instructor setup

The inspector reports world, lane, lesson, coordinates, instance ID and type ID. Its classroom survey includes unloaded chunks in the registered regions. The follow-up reports indexed copies of the same type, recorded direct clones and duplicate ID locations. Use `/bb survey` after an external WorldEdit paste. Older worlds carry varying amounts of clone history.

| Action | In-game entry |
| --- | --- |
| Give every source box in a lesson an additional stack, including its starting inventory for future resets | Hold the configured stack and use `/bb preview add`. Use `/bb preview add hotbar` for several instruction stacks. |
| Return a row to its starting inventory and clear learner programs | `/bb preview reset` |
| Replace the matching lesson slot across lanes from the selected template | `/bb preview overwrite` |
| Copy to an exact start-button coordinate | `/bb preview paste <x> <y> <z>` |
| Make a rectangular array of copies | `/bb preview cascade <x> <y> <z> <countX> <countZ> <spacingX> <spacingZ>`. Coordinates name the first destination button. Negative counts reverse that axis. |
| Repair the selected row’s IDs during setup | `/bb preview repair`. This assigns fresh instance IDs and restores saved supplies. |
| Repair a single freestanding puzzle outside the registered rows | `/bb preview repair-here` |
| Restore the most recent setup operation | `/bb undo`, inspect the destination count, then confirm. The undo snapshot lasts for the running server session. Its preview identifies destinations changed since setup, and confirmation rechecks those destinations. |

The preview shows the source in yellow and nearby destinations in aqua. The text lists distant destinations, IDs, affected bounds and occupancy. A classroom-wide import pages its destinations with `/bb targets <page>`. `/bb conflicts` lists excluded import locations. Bulk setup work is serialized across instructors. Preview tickets load distant chunks and are released after completion, cancellation or expiry.

**Apply to lane 2 only** runs a test from the current preview. **Apply remaining destinations** then skips targets completed by that preview. A newly created addition preview represents a new addition. Setup operations check destinations again before writing and report completed lanes if the batch stops. An overwrite can replace coincident managers with the same affected footprint. Intersecting unrelated puzzles or different-sized existing bounds require resolving the geometry first.

Use **reset** to clear programs and restore supplies. Use **overwrite** when the physical course itself needs to be rebuilt from a template. The group reset aliases in the Maryville archive also affect accounts, ranks, or quest data and have a broader scope.

## Maryville notes

The reset-day world contains a named WorldGuard region for **puzzle13-function-pairs** and no lane 1 start button inside that region. The same is true of the three named create regions. Learners can author puzzles in their assigned creative spaces. The lesson picker offers individual anchors when a template lane has more than one start button, as it does for **puzzle5-conditionals**.

The supplied reset-day entity files contain 490 puzzle managers. Of those, 481 start buttons lie inside a numbered lane or a named lesson region. Nine more are east of the classroom at Z = −827, from X = 441 through X = 657. The classroom import uses the registered geometry. Paper also reports a recoverable legacy biome name, `minecraft:modified_badlands_plateau`, while converting some archived chunks. An instructor can inspect and repair an outlying puzzle individually.

For a server move, keep the world, its scoreboard, and `plugins/BarrelbotClassroom/config.yml` together. Puzzle associations and managed instruction addresses include a world identity.
