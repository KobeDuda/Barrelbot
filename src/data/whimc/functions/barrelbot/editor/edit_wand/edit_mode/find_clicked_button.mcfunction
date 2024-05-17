#> whimc:barrelbot/editor/edit_wand/edit_mode/find_clicked_button
#   Returns which button was clicked

#> Inputs:
#   - #u_proj whimc.dummy     | Coords in screen basis of the clicked spot
#   - #v_proj whimc.dummy     | 
#
#   - whimc:storage Page      | Page data

#> Outputs:
#   - whimc:storage ClickedButton

#Loop through all buttons
data modify storage whimc:storage Buttons set from storage whimc:storage Page.Buttons
execute store result score $buttons whimc.dummy if data storage whimc:storage Buttons[]
execute if score $buttons whimc.dummy matches 1.. run function whimc:barrelbot/editor/edit_wand/edit_mode/find_clicked_button_loop