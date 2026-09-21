import {describe, it, expect, vi, afterEach} from "vitest"
import {defineComponent} from "vue"
import {mount} from "@vue/test-utils"

import {useBlockEditorKeyboard, type BlockEditorKeyBindingLike} from "../../../../../src/components/no-code/blocks/useBlockEditorKeyboard"

const KEYMAP: BlockEditorKeyBindingLike[] = [
    {id: "move", keys: ["ArrowDown"], alt: ["j"]},
    {id: "delete", keys: ["Backspace", "Delete"]},
    {id: "command-menu", keys: ["Meta+Shift+p", "Control+Shift+p"]},
    {id: "save", keys: ["Meta+s", "Control+s"]},
    {id: "clear", keys: ["Escape"]},
    {id: "help", keys: ["?"]},
    {id: "focus-panel", keys: ["Tab"]},
    {id: "insert-after", keys: ["a"]},
    {id: "insert-before", keys: ["Shift+a"]},
]

function mountWithKeyboard(dispatch: (id: string, event: KeyboardEvent) => void | boolean, isOverlayOpen?: () => boolean) {
    const Comp = defineComponent({
        setup() {
            useBlockEditorKeyboard({keymap: KEYMAP, dispatch, isOverlayOpen})
            return () => null
        },
    })
    return mount(Comp)
}

function dispatchKeydown(target: EventTarget, options: KeyboardEventInit) {
    const event = new KeyboardEvent("keydown", {bubbles: true, cancelable: true, ...options})
    target.dispatchEvent(event)
    return event
}

describe("useBlockEditorKeyboard", () => {
    let wrapper: ReturnType<typeof mountWithKeyboard> | undefined

    afterEach(() => {
        wrapper?.unmount()
        wrapper = undefined
    })

    it("dispatches the matching binding id for a plain key on the window", () => {
        // Given
        const dispatch = vi.fn()
        wrapper = mountWithKeyboard(dispatch)

        // When
        dispatchKeydown(window, {key: "ArrowDown"})

        // Then
        expect(dispatch).toHaveBeenCalledTimes(1)
        expect(dispatch).toHaveBeenCalledWith("move", expect.any(KeyboardEvent))
    })

    it("resolves an alt key to the same binding", () => {
        // Given
        const dispatch = vi.fn()
        wrapper = mountWithKeyboard(dispatch)

        // When
        dispatchKeydown(window, {key: "j"})

        // Then
        expect(dispatch).toHaveBeenCalledWith("move", expect.any(KeyboardEvent))
    })

    it("ignores keys typed into an input element", () => {
        // Given
        const dispatch = vi.fn()
        wrapper = mountWithKeyboard(dispatch)
        const input = document.createElement("input")
        document.body.appendChild(input)

        // When
        dispatchKeydown(input, {key: "Delete"})

        // Then
        expect(dispatch).not.toHaveBeenCalled()
        document.body.removeChild(input)
    })

    it("ignores keys typed into a textarea element", () => {
        // Given
        const dispatch = vi.fn()
        wrapper = mountWithKeyboard(dispatch)
        const textarea = document.createElement("textarea")
        document.body.appendChild(textarea)

        // When
        dispatchKeydown(textarea, {key: "d"})

        // Then
        expect(dispatch).not.toHaveBeenCalled()
        document.body.removeChild(textarea)
    })

    it("ignores keys typed inside a contenteditable element", () => {
        // Given — jsdom does not compute isContentEditable from the attribute, so it is stubbed directly
        const dispatch = vi.fn()
        wrapper = mountWithKeyboard(dispatch)
        const div = document.createElement("div")
        Object.defineProperty(div, "isContentEditable", {value: true})
        document.body.appendChild(div)

        // When
        dispatchKeydown(div, {key: "Delete"})

        // Then
        expect(dispatch).not.toHaveBeenCalled()
        document.body.removeChild(div)
    })

    it("ignores keys typed inside a Monaco editor", () => {
        // Given
        const dispatch = vi.fn()
        wrapper = mountWithKeyboard(dispatch)
        const monacoRoot = document.createElement("div")
        monacoRoot.className = "monaco-editor"
        const inner = document.createElement("span")
        monacoRoot.appendChild(inner)
        document.body.appendChild(monacoRoot)

        // When
        dispatchKeydown(inner, {key: "Delete"})

        // Then
        expect(dispatch).not.toHaveBeenCalled()
        document.body.removeChild(monacoRoot)
    })

    it("still dispatches Escape while typing in an input", () => {
        // Given
        const dispatch = vi.fn()
        wrapper = mountWithKeyboard(dispatch)
        const input = document.createElement("input")
        document.body.appendChild(input)

        // When
        dispatchKeydown(input, {key: "Escape"})

        // Then
        expect(dispatch).toHaveBeenCalledWith("clear", expect.any(KeyboardEvent))
        document.body.removeChild(input)
    })

    it("still dispatches Cmd+S while typing in an input", () => {
        // Given
        const dispatch = vi.fn()
        wrapper = mountWithKeyboard(dispatch)
        const input = document.createElement("input")
        document.body.appendChild(input)

        // When
        dispatchKeydown(input, {key: "s", metaKey: true})

        // Then
        expect(dispatch).toHaveBeenCalledWith("save", expect.any(KeyboardEvent))
        document.body.removeChild(input)
    })

    it("blocks canvas shortcuts while an overlay owns the keys", () => {
        // Given
        const dispatch = vi.fn()
        wrapper = mountWithKeyboard(dispatch, () => true)

        // When
        dispatchKeydown(window, {key: "ArrowDown"})

        // Then
        expect(dispatch).not.toHaveBeenCalled()
    })

    it("still dispatches the command menu shortcut while an overlay is open", () => {
        // Given
        const dispatch = vi.fn()
        wrapper = mountWithKeyboard(dispatch, () => true)

        // When
        dispatchKeydown(window, {key: "p", metaKey: true, shiftKey: true})

        // Then
        expect(dispatch).toHaveBeenCalledWith("command-menu", expect.any(KeyboardEvent))
    })

    it("does not dispatch the command menu shortcut for plain Cmd+K (reserved by the app-wide global search)", () => {
        // Given
        const dispatch = vi.fn()
        wrapper = mountWithKeyboard(dispatch)

        // When
        dispatchKeydown(window, {key: "k", metaKey: true})

        // Then
        expect(dispatch).not.toHaveBeenCalled()
    })

    it("still dispatches help to close itself while it is the open overlay", () => {
        // Given
        const dispatch = vi.fn()
        wrapper = mountWithKeyboard(dispatch, () => true)

        // When — "?" is itself a shift-produced character on common layouts
        dispatchKeydown(window, {key: "?", shiftKey: true})

        // Then
        expect(dispatch).toHaveBeenCalledWith("help", expect.any(KeyboardEvent))
    })

    it("does not preventDefault when dispatch reports it did not handle the key", () => {
        // Given — e.g. Tab pressed with no dock panel open to focus into
        const dispatch = vi.fn().mockReturnValue(false)
        wrapper = mountWithKeyboard(dispatch)

        // When
        const event = dispatchKeydown(window, {key: "Tab"})

        // Then — native Tab behavior is left alone
        expect(dispatch).toHaveBeenCalledWith("focus-panel", expect.any(KeyboardEvent))
        expect(event.defaultPrevented).toBe(false)
    })

    it("still preventDefaults when dispatch handles the key", () => {
        // Given — e.g. Tab pressed while a dock panel is open
        const dispatch = vi.fn().mockReturnValue(true)
        wrapper = mountWithKeyboard(dispatch)

        // When
        const event = dispatchKeydown(window, {key: "Tab"})

        // Then
        expect(event.defaultPrevented).toBe(true)
    })

    it("resolves plain 'a' to insert-after, not insert-before", () => {
        // Given
        const dispatch = vi.fn()
        wrapper = mountWithKeyboard(dispatch)

        // When
        dispatchKeydown(window, {key: "a"})

        // Then
        expect(dispatch).toHaveBeenCalledWith("insert-after", expect.any(KeyboardEvent))
    })

    it("resolves Shift+a to insert-before, not insert-after", () => {
        // Given — without shift-aware disambiguation, "a" (which doesn't request
        // Shift) would match first and insert-before would be unreachable
        const dispatch = vi.fn()
        wrapper = mountWithKeyboard(dispatch)

        // When
        dispatchKeydown(window, {key: "A", shiftKey: true})

        // Then
        expect(dispatch).toHaveBeenCalledWith("insert-before", expect.any(KeyboardEvent))
        expect(dispatch).toHaveBeenCalledTimes(1)
    })

    it("does not dispatch after the component using it unmounts", () => {
        // Given
        const dispatch = vi.fn()
        wrapper = mountWithKeyboard(dispatch)
        wrapper.unmount()
        wrapper = undefined

        // When
        dispatchKeydown(window, {key: "ArrowDown"})

        // Then
        expect(dispatch).not.toHaveBeenCalled()
    })

    it("ignores an unmapped key", () => {
        // Given
        const dispatch = vi.fn()
        wrapper = mountWithKeyboard(dispatch)

        // When
        dispatchKeydown(window, {key: "z"})

        // Then
        expect(dispatch).not.toHaveBeenCalled()
    })
})
