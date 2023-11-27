#> whimc:barrelbot/clone/function_display_setup
#   Sets up the item display that hovers above a function barrel

data merge entity @s {brightness:{sky:15,block:15},billboard:"center",transformation:{scale:[0.5f,0.5f,0.0f]}}
data modify entity @s item set from storage whimc:storage display_item
tag @s add whimc.function_display