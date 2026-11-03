export const EDITOR_FONT_FAMILY_STORAGE_KEY = "editorFontFamily"

export const DEFAULT_EDITOR_FONT_FAMILY = "'JetBrains Mono', monospace"

export function editorFontFamily(): string {
    return localStorage.getItem(EDITOR_FONT_FAMILY_STORAGE_KEY) || DEFAULT_EDITOR_FONT_FAMILY
}
