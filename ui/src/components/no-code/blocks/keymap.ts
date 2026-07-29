export type BlockEditorKeymapGroup = "navigate" | "insert" | "edit" | "global"

export interface BlockEditorKeyBinding {
    id: string
    keys: string[]
    alt?: string[]
    group: BlockEditorKeymapGroup
    i18nKey: string
}

export const BLOCK_EDITOR_KEYMAP: BlockEditorKeyBinding[] = [
    {id: "move", keys: ["ArrowUp", "ArrowDown"], alt: ["k", "j"], group: "navigate", i18nKey: "block_editor.shortcuts.move_between"},
    {id: "step-into", keys: ["ArrowRight"], alt: ["l"], group: "navigate", i18nKey: "block_editor.shortcuts.step_into"},
    {id: "step-out", keys: ["ArrowLeft"], alt: ["h"], group: "navigate", i18nKey: "block_editor.shortcuts.step_out"},
    // Space mirrors the native button-activation key the cards supported when
    // they still owned their own keydown handlers (activation now lives here).
    {id: "open", keys: ["Enter"], alt: ["e", " "], group: "navigate", i18nKey: "block_editor.shortcuts.open"},
    {id: "open-split", keys: ["Meta+Enter", "Control+Enter"], group: "navigate", i18nKey: "block_editor.shortcuts.open_split"},
    {id: "clear", keys: ["Escape"], group: "navigate", i18nKey: "block_editor.shortcuts.clear"},

    {id: "insert-after", keys: ["a"], alt: ["n"], group: "insert", i18nKey: "block_editor.shortcuts.add_after"},
    {id: "insert-before", keys: ["Shift+a"], alt: ["Shift+n"], group: "insert", i18nKey: "block_editor.shortcuts.add_before"},
    {id: "quick-insert", keys: ["/"], group: "insert", i18nKey: "block_editor.shortcuts.add_task"},
    // Meta+K alone is already the app-wide "Jump to" global search (see GlobalSearch.vue).
    // Meta+Shift+K was tried first but collides with shortcuts in common companion apps
    // (e.g. Notion) that can fire even while this tab has focus — Meta+Shift+P (the
    // conventional "command palette" binding in VS Code, Slack, etc.) is free instead.
    {id: "command-menu", keys: ["Meta+Shift+p", "Control+Shift+p"], group: "insert", i18nKey: "block_editor.shortcuts.command_palette"},

    {id: "duplicate", keys: ["d"], group: "edit", i18nKey: "block_editor.duplicate"},
    {id: "delete", keys: ["Backspace", "Delete"], group: "edit", i18nKey: "block_editor.delete"},
    {id: "reorder", keys: ["Alt+ArrowUp", "Alt+ArrowDown"], group: "edit", i18nKey: "block_editor.shortcuts.reorder"},

    {id: "save", keys: ["Meta+s", "Control+s"], group: "global", i18nKey: "block_editor.shortcuts.save"},
    {id: "undo", keys: ["Meta+z", "Control+z"], group: "global", i18nKey: "block_editor.shortcuts.undo"},
    {id: "help", keys: ["?"], group: "global", i18nKey: "block_editor.shortcuts.toggle"},
]

export function findBlockEditorBinding(id: string): BlockEditorKeyBinding | undefined {
    return BLOCK_EDITOR_KEYMAP.find(binding => binding.id === id)
}

export function blockEditorKeymapByGroup(group: BlockEditorKeymapGroup): BlockEditorKeyBinding[] {
    return BLOCK_EDITOR_KEYMAP.filter(binding => binding.group === group)
}
