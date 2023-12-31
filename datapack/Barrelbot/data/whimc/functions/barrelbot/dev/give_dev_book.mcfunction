give @s written_book{barrelbot_dev_book:1b,display:{Name:'{"text":"Barrelbot Dev Book","color":"gold","bold":true,"italic":false}'},title:"",author:"",pages:[\
    '[\
        {"text":"Contents\\n"},\
        {"text":"Features\\n","color":"blue","clickEvent":{"action":"change_page","value":"2"}},\
        {"text":"Puzzle\\n","color":"blue","clickEvent":{"action":"change_page","value":"3"}},\
        {"text":"Instructions\\n","color":"blue","clickEvent":{"action":"change_page","value":"4"}},\
        {"text":"Clone\\n","color":"blue","clickEvent":{"action":"change_page","value":"5"}}\
    ]',\
    '[\
        {"text":"Features\\n"},\
        {"text":"Spawn Bot\\n","color":"blue","clickEvent":{"action":"run_command","value":"/function whimc:barrelbot/dev/spawn_bot"}},\
        {"text":"Delete Bot\\n","color":"blue","clickEvent":{"action":"run_command","value":"/function whimc:barrelbot/dev/delete_bot"}},\
        {"text":"Convert to Dispenser\\n","color":"blue","clickEvent":{"action":"run_command","value":"/function whimc:barrelbot/dev/convert_to_dispenser"}},\
        {"text":"Convert to Barrel\\n","color":"blue","clickEvent":{"action":"run_command","value":"/function whimc:barrelbot/dev/convert_to_barrel"}},\
        {"text":"Create Function\\n","color":"blue","clickEvent":{"action":"run_command","value":"/function whimc:barrelbot/dev/create_function"}},\
        {"text":"Delete Function\\n","color":"blue","clickEvent":{"action":"run_command","value":"/function whimc:barrelbot/dev/delete_function"}},\
        {"text":"Link Function Item\\n","color":"blue","clickEvent":{"action":"run_command","value":"/function whimc:barrelbot/dev/link_function_item"}}\
    ]',\
    '[\
        {"text":"Puzzle\\n"},\
        {"text":"Link to Puzzle\\n","color":"blue","clickEvent":{"action":"run_command","value":"/function whimc:barrelbot/dev/link_puzzle"}},\
        {"text":"Create Puzzle\\n","color":"blue","clickEvent":{"action":"run_command","value":"/function whimc:barrelbot/dev/spawn_manager"}},\
        {"text":"Delete Puzzle\\n","color":"blue","clickEvent":{"action":"run_command","value":"/function whimc:barrelbot/dev/delete_puzzle"}},\
        {"text":"Set Success Condition\\n","color":"blue","clickEvent":{"action":"copy_to_clipboard","value":"/data modify entity @e[type=marker,limit=1,tag=whimc.barrelbot.puzzle_storage,sort=nearest] data.success_function set value \\"\\""},"hoverEvent":{"action":"show_text","value":[{"text":"Copy to Clipboard"}]}},\
        {"text":"Set Puzzle Items\\n","color":"blue","clickEvent":{"action":"run_command","value":"/function whimc:barrelbot/dev/lock_puzzle_items"}},\
        {"text":"Reset All Puzzles\\n","color":"blue","clickEvent":{"action":"run_command","value":"/function whimc:barrelbot/dev/reset_all_puzzles"}}\
    ]',\
    '[\
        {"text":"Instructions\\n"},\
        {"text":"Move Down\\n","color":"blue","clickEvent":{"action":"run_command","value":"/function whimc:barrelbot/instructions/move_down/give"}},\
        {"text":"Move Forward\\n","color":"blue","clickEvent":{"action":"run_command","value":"/function whimc:barrelbot/instructions/move_forward/give"}},\
        {"text":"Turn Left\\n","color":"blue","clickEvent":{"action":"run_command","value":"/function whimc:barrelbot/instructions/turn_left/give"}},\
        {"text":"Turn Right\\n","color":"blue","clickEvent":{"action":"run_command","value":"/function whimc:barrelbot/instructions/turn_right/give"}},\
        {"text":"Counter Loop\\n","color":"blue","clickEvent":{"action":"copy_to_clipboard","value":"/function whimc:barrelbot/instructions/loop/counter/give {cycles:}"},"hoverEvent":{"action":"show_text","value":[{"text":"Copy to Clipboard"}]}},\
        {"text":"While Loop\\n","color":"blue","clickEvent":{"action":"copy_to_clipboard","value":"/function whimc:barrelbot/instructions/loop/while/give {condition:\\"\\",description:\\"\\"}"},"hoverEvent":{"action":"show_text","value":[{"text":"Copy to Clipboard"}]}},\
        {"text":"Infinite Loop\\n","color":"blue","clickEvent":{"action":"run_command","value":"/function whimc:barrelbot/instructions/loop/give_infinite"}},\
        {"text":"If Statement\\n","color":"blue","clickEvent":{"action":"copy_to_clipboard","value":"/function whimc:barrelbot/instructions/if/give {condition:\\"\\",description:\\"\\"}"},"hoverEvent":{"action":"show_text","value":[{"text":"Copy to Clipboard"}]}},\
        {"text":"End Code Block\\n","color":"blue","clickEvent":{"action":"run_command","value":"/function whimc:barrelbot/instructions/end_code_block/give"}}\
    ]',\
    '[\
        {"text":"Clone\\n"},\
        {"text":"Start clone\\n","color":"blue","clickEvent":{"action":"run_command","value":"/function whimc:barrelbot/dev/clone/begin_clone"}},\
        {"text":"End clone\\n","color":"blue","clickEvent":{"action":"run_command","value":"/function whimc:barrelbot/dev/clone/end_clone"}},\
        {"text":"Set Position 1\\n","color":"blue","clickEvent":{"action":"run_command","value":"/function whimc:barrelbot/dev/clone/set_pos1"}},\
        {"text":"Set Position 2\\n","color":"blue","clickEvent":{"action":"run_command","value":"/function whimc:barrelbot/dev/clone/set_pos2"}},\
        {"text":"Paste Puzzle\\n","color":"blue","clickEvent":{"action":"run_command","value":"/function whimc:barrelbot/dev/clone/paste_puzzle"}}\
    ]'\
]}