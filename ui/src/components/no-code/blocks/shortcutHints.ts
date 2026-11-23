import {blockEditorKeymapByGroup, findBlockEditorBinding, type BlockEditorKeyBinding, type BlockEditorKeymapGroup} from "./keymap"

export interface FooterHint {
    id: string
    keys: string[]
    i18nKey: string
}

export function computeIsMac(nav: {platform?: string; userAgent?: string} = navigator): boolean {
    return /Mac|iPhone|iPod|iPad/i.test(nav.platform || nav.userAgent || "")
}

/** Keys with no modifier: shown verbatim, exactly as the binding declares them. */
const SIMPLE_KEY_DISPLAY: Record<string, string> = {
    ArrowUp: "↑",
    ArrowDown: "↓",
    ArrowLeft: "←",
    ArrowRight: "→",
    Enter: "↵",
    " ": "Space",
    Backspace: "⌫",
    Delete: "⌦",
}

/**
 * `Meta` (Mac) and `Control` (Windows/Linux) are never two distinct bindings in this keymap — the
 * same shortcut written once per OS convention — so both resolve to the platform's own glyph
 * rather than always to `⌘`, which read wrong on Windows and Linux.
 */
export function buildModifierDisplay(isMac: boolean): Record<string, string> {
    return isMac
        ? {Meta: "⌘", Control: "⌘", Shift: "⇧", Alt: "⌥"}
        : {Meta: "Ctrl+", Control: "Ctrl+", Shift: "Shift+", Alt: "Alt+"}
}

const MODIFIER_DISPLAY: Record<string, string> = buildModifierDisplay(computeIsMac())

function comboMainKeyDisplay(key: string): string {
    if (key in SIMPLE_KEY_DISPLAY) return SIMPLE_KEY_DISPLAY[key]
    return key.length === 1 ? key.toUpperCase() : key
}

export function displayForKey(key: string, modifiers: Record<string, string> = MODIFIER_DISPLAY): string {
    if (!key.includes("+")) return SIMPLE_KEY_DISPLAY[key] ?? key
    const parts = key.split("+")
    const mainKey = comboMainKeyDisplay(parts[parts.length - 1])
    const prefix = parts.slice(0, -1).map(mod => modifiers[mod] ?? `${mod}+`).join("")
    return `${prefix}${mainKey}`
}

const SHORTCUT_GROUP_ORDER: BlockEditorKeymapGroup[] = ["navigate", "insert", "edit", "global"]

const HIDDEN_SHORTCUT_IDS = new Set(["clear"])
const CLIPBOARD_SHORTCUT_IDS = new Set(["copy", "cut", "paste"])

export function displayKeys(keys: string[]): string[] {
    const seen = new Set<string>()
    const result: string[] = []
    for (const key of keys) {
        const display = displayForKey(key)
        if (seen.has(display)) continue
        seen.add(display)
        result.push(display)
    }
    return result
}

/**
 * The topology canvas shares this keymap but does not (yet) wire clipboard actions into its own
 * dispatcher, so `supportsClipboard: false` keeps its `?` overlay from advertising a shortcut that
 * silently does nothing there.
 */
export function buildShortcutGroups(
    options: {supportsClipboard?: boolean} = {},
): {group: BlockEditorKeymapGroup; bindings: BlockEditorKeyBinding[]}[] {
    const supportsClipboard = options.supportsClipboard ?? true
    return SHORTCUT_GROUP_ORDER.map(group => ({
        group,
        bindings: blockEditorKeymapByGroup(group).filter(binding =>
            !HIDDEN_SHORTCUT_IDS.has(binding.id) && (supportsClipboard || !CLIPBOARD_SHORTCUT_IDS.has(binding.id)),
        ),
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
