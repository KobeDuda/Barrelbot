#> whimc:barrelbot/editor/outline/adjust/min_z

execute store result entity @s transformation.scale[0] float -40 run scoreboard players get $size_x whimc.dummy
execute store result entity @s transformation.scale[1] float 40 run scoreboard players get $size_y whimc.dummy

execute store result entity @s transformation.translation[0] float 1 run scoreboard players get $size_x whimc.dummy