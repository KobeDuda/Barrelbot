#> whimc:barrelbot/editor/edit_wand/edit_mode/refresh_text
#   Refreshes all linked text displays

#Can be called either from player or root entity

scoreboard players operation $temp whimc.barrelbot.edit_instance_id = @s whimc.barrelbot.edit_instance_id

execute as @e[type=text_display,tag=whimc.edit_text_display,predicate=whimc:barrelbot/match_edit_id] run function whimc:barrelbot/editor/edit_wand/edit_mode/refresh_text_display