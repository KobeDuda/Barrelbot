execute if block ~ ~ ~ #whimc:barrelbot as @e[type=item_display, tag=whimc.barrelbot, dx=0, dy=0, dz=0, limit=1] run function whimc:barrelbot/editor/edit_wand/barrelbot_found
execute if block ~ ~ ~ #minecraft:buttons as @e[type=text_display, tag=whimc.barrelbot.puzzle_manager, dx=0, dy=0, dz=0, limit=1] run function whimc:barrelbot/editor/edit_wand/puzzle_button_found
execute if block ~ ~ ~ #minecraft:shulker_boxes as @e[type=marker, tag=whimc.barrelbot.function, dx=0, dy=0, dz=0, limit=1] run function whimc:barrelbot/editor/edit_wand/function_found
execute if block ~ ~ ~ minecraft:yellow_shulker_box run function whimc:barrelbot/editor/edit_wand/check_if_puzzle_storage
