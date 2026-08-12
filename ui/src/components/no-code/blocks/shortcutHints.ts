import {blockEditorKeymapByGroup, findBlockEditorBinding, type BlockEditorKeyBinding, type BlockEditorKeymapGroup} from "./keymap"

export interface FooterHint {
    id: string
    keys: string[]
    i18nKey: string
}

const KEY_DISPLAY: Record<string, string> = {
    ArrowUp: "↑",
    ArrowDown: "↓",
    ArrowLeft: "←",
    ArrowRight: "→",
    Enter: "↵",
    "Meta+Enter": "⌘↵",
    "Control+Enter": "⌘↵",
    " ": "Space",
    Backspace: "⌫",
    Delete: "⌦",
    "Meta+Shift+p": "⌘⇧P",
    "Control+Shift+p": "⌘⇧P",
    "Meta+s": "⌘S",
    "Control+s": "⌘S",
    "Meta+z": "⌘Z",
    "Control+z": "⌘Z",
    "Alt+ArrowUp": "⌥↑",
    "Alt+ArrowDown": "⌥↓",
}

const SHORTCUT_GROUP_ORDER: BlockEditorKeymapGroup[] = ["navigate", "insert", "edit", "global"]

const HIDDEN_SHORTCUT_IDS = new Set(["clear"])

export function displayKeys(keys: string[]): string[] {
    const seen = new Set<string>()
    const result: string[] = []
    for (const key of keys) {
        const display = KEY_DISPLAY[key] ?? key
        if (seen.has(display)) continue
        seen.add(display)
        result.push(display)
    }
    return result
}

export function buildShortcutGroups(): {group: BlockEditorKeymapGroup; bindings: BlockEditorKeyBinding[]}[] {
    return SHORTCUT_GROUP_ORDER.map(group => ({
        group,
        bindings: blockEditorKeymapByGroup(group).filter(binding => !HIDDEN_SHORTCUT_IDS.has(binding.id)),
    }))
}

function keysFor(id: string): string[] {
    return findBlockEditorBinding(id)?.keys ?? []
}

export function buildFooterHints(state: {overlayOpen: boolean; realBlockFocused: boolean}): FooterHint[] {
    if (state.overlayOpen) {
        return [
            {id: "move", keys: ["ArrowUp", "ArrowDown"], i18nKey: "block_editor.kbd_navigate"},
            {id: "run", keys: ["Enter"], i18nKey: "block_editor.kbd_add"},
            {id: "close", keys: ["Escape"], i18nKey: "block_editor.kbd_close"},
        ]
    }

    return [
        {id: "help", keys: keysFor("help"), i18nKey: "block_editor.shortcuts.toggle"},
        {id: "move", keys: keysFor("move"), i18nKey: "block_editor.shortcuts.move_between"},
        {id: "open", keys: keysFor("open"), i18nKey: "block_editor.shortcuts.open"},
        {id: "insert", keys: keysFor("insert-after"), i18nKey: "block_editor.shortcuts.add_after"},
        ...(state.realBlockFocused
            ? [
                {id: "insert-before", keys: keysFor("insert-before"), i18nKey: "block_editor.shortcuts.add_before"},
                {id: "reorder", keys: keysFor("reorder"), i18nKey: "block_editor.shortcuts.reorder"},
            ]
            : []),
        {id: "command-menu", keys: keysFor("command-menu"), i18nKey: "block_editor.shortcuts.command_palette"},
    ]
}
