#> whimc:barrelbot/editor/outline/spawn/max_z

scoreboard players operation @s whimc.barrelbot.puzzle_id = $temp whimc.barrelbot.puzzle_id

tag @s add whimc.editor_outline
tag @s add whimc.editor_outline.max_z
data merge entity @s {see_through:1b,brightness:{sky:15,block:15},background:-2145286754}

execute store result entity @s transformation.scale[0] float 40 run scoreboard players get $size_x whimc.dummy
execute store result entity @s transformation.scale[1] float 40 run scoreboard players get $size_y whimc.dummy

execute store result entity @s transformation.translation[2] float 1 run scoreboard players get $size_z whimc.dummy