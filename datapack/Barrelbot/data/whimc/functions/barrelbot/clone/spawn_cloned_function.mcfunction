#> whimc:barrelbot/clone/spawn_cloned_function
#   Spawns a new function entity in the new location

scoreboard players operation $temp whimc.barrelbot.puzzle_id = $new_id whimc.barrelbot.puzzle_id
execute align xyz positioned ~0.5 ~0.5 ~0.5 summon marker at @s run function whimc:barrelbot/clone/function_setup