export const CHIP_DRAG_MIME = "application/x-kestra-chip"

export function isArmableField(el: EventTarget | null): el is HTMLInputElement | HTMLTextAreaElement {
    if (el instanceof HTMLTextAreaElement) {
        return !el.disabled && !el.readOnly
    }

    if (el instanceof HTMLInputElement) {
        return !el.disabled && !el.readOnly && (el.type === "text" || el.type === "search" || el.type === "")
    }

    return false
}

export function insertAtCaret(field: HTMLInputElement | HTMLTextAreaElement, text: string): void {
    const value = field.value ?? ""
    const start = field.selectionStart ?? value.length
    const end = field.selectionEnd ?? value.length

    field.value = value.slice(0, start) + text + value.slice(end)

    const caret = start + text.length
    try {
        field.setSelectionRange(caret, caret)
    } catch {
        // setSelectionRange throws on some input types (e.g. number)
    }

    field.dispatchEvent(new Event("input", {bubbles: true}))
}
