scoreboard players set $step_out whimc.dummy 0
scoreboard players set $step_type whimc.dummy 0
execute if data storage whimc:storage marker_data.stack[-1].loop run scoreboard players set $step_type whimc.dummy 1
execute if data storage whimc:storage marker_data.stack[-1].if run scoreboard players set $step_type whimc.dummy 2
execute if score $step_type whimc.dummy matches 0 run function whimc:barrelbot/instructions/function/check_step_out
execute if score $step_type whimc.dummy matches 1 run function whimc:barrelbot/instructions/loop/check_step_out
execute if score $step_type whimc.dummy matches 2 run function whimc:barrelbot/instructions/if/check_step_out
execute if score $step_out whimc.dummy matches 1 run function whimc:barrelbot/functions/check_step_out
