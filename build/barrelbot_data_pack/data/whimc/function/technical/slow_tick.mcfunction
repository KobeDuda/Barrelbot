execute as @e[type=item_display, tag=whimc.barrelbot,tag=!whimc.classroom_running] at @s if loaded ~ ~ ~ if block ~ ~ ~ air run function whimc:barrelbot/bot/kill
execute as @e[type=item_display, tag=whimc.instructor] at @s if loaded ~ ~ ~ if block ~ ~ ~ air run function whimc:barrelbot/block/instructor/kill
execute as @e[type=marker, tag=whimc.barrelbot.function] at @s if loaded ~ ~ ~ if block ~ ~ ~ air run function whimc:barrelbot/functions/delete
execute as @e[type=text_display, tag=whimc.barrelbot.puzzle_manager] at @s if loaded ~ ~ ~ if block ~ ~ ~ air run function whimc:barrelbot/manager/delete_puzzle
scoreboard players set #slow_tick whimc.dummy 0
