data modify storage whimc:storage Page set value {Buttons: [], Text: []}
data modify storage whimc:storage Page.key set value "puzzle"
data modify storage whimc:storage Page.Buttons append value {item: {id: "minecraft:white_stained_glass_pane", tag: {CustomModelData: 130005}}, x_min: -125, x_max: 125, y_min: -875, y_max: -625, command: "function whimc:barrelbot/editor/edit_wand/edit_mode/pages/close", hover_text: "Exit Menu"}
data modify storage whimc:storage Page.Buttons append value {item: {id: "minecraft:white_stained_glass_pane", tag: {CustomModelData: 130003}}, x_min: 299, x_max: 600, y_min: -500, y_max: -199, command: "function whimc:barrelbot/editor/edit_wand/edit_mode/pages/toggle_size_editor", hover_text: "Toggle Size Editor"}
data modify storage whimc:storage Page.Buttons append value {item: {id: "minecraft:white_stained_glass_pane", tag: {CustomModelData: 130002}}, x_min: 0, x_max: 300, y_min: -500, y_max: -199, page: "success_conditions_0", hover_text: "Set Success Condition"}
data modify storage whimc:storage Page.Buttons append value {item: {id: "minecraft:white_stained_glass_pane", tag: {CustomModelData: 130012}}, x_min: -300, x_max: 0, y_min: -500, y_max: -199, command: "function whimc:barrelbot/editor/edit_wand/edit_mode/pages/reset_puzzle", hover_text: "Reset Puzzle"}
data modify storage whimc:storage Page.Buttons append value {item: {id: "minecraft:white_stained_glass_pane", tag: {CustomModelData: 130013}}, x_min: -600, x_max: -299, y_min: -500, y_max: -199, command: "function whimc:barrelbot/editor/edit_wand/edit_mode/pages/name_puzzle", hover_text: "Assign Puzzle Name"}
