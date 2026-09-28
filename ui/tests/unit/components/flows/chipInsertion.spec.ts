import {describe, it, expect, vi} from "vitest"
import {insertAtCaret, isArmableField} from "../../../../src/components/flows/chipInsertion"

function makeField(value: string, selectionStart: number, selectionEnd = selectionStart): HTMLTextAreaElement {
    const field = document.createElement("textarea")
    field.value = value
    field.setSelectionRange(selectionStart, selectionEnd)
    return field
}

describe("insertAtCaret", () => {
    it("inserts at the start of the field", () => {
        const field = makeField("world", 0)
        insertAtCaret(field, "hello ")

        expect(field.value).toBe("hello world")
        expect(field.selectionStart).toBe(6)
        expect(field.selectionEnd).toBe(6)
    })

    it("inserts in the middle of the field", () => {
        const field = makeField("Ab", 1)
        insertAtCaret(field, "XY")

        expect(field.value).toBe("AXYb")
        expect(field.selectionStart).toBe(3)
    })

    it("inserts at the end of the field", () => {
        const field = makeField("hello", 5)
        insertAtCaret(field, " world")

        expect(field.value).toBe("hello world")
        expect(field.selectionStart).toBe(11)
    })

    it("replaces the current selection", () => {
        const field = makeField("hello world", 0, 5)
        insertAtCaret(field, "goodbye")

        expect(field.value).toBe("goodbye world")
        expect(field.selectionStart).toBe(7)
    })

    it("inserts into an empty field", () => {
        const field = makeField("", 0)
        insertAtCaret(field, "{{ inputs.file }}")

        expect(field.value).toBe("{{ inputs.file }}")
        expect(field.selectionStart).toBe(17)
    })

    it("dispatches a bubbling input event so v-model reacts", () => {
        const field = makeField("", 0)
        const handler = vi.fn()
        const parent = document.createElement("div")
        parent.appendChild(field)
        parent.addEventListener("input", handler)

        insertAtCaret(field, "{{ now() }}")

        expect(handler).toHaveBeenCalledTimes(1)
    })
})

describe("isArmableField", () => {
    it("accepts an enabled textarea", () => {
        expect(isArmableField(document.createElement("textarea"))).toBe(true)
    })

    it("accepts a plain text input", () => {
        const input = document.createElement("input")
        expect(isArmableField(input)).toBe(true)
    })

    it("rejects a disabled field", () => {
        const field = document.createElement("textarea")
        field.disabled = true
        expect(isArmableField(field)).toBe(false)
    })

    it("rejects a readonly field", () => {
        const field = document.createElement("textarea")
        field.readOnly = true
        expect(isArmableField(field)).toBe(false)
    })

    it("rejects a non-text input type", () => {
        const input = document.createElement("input")
        input.type = "checkbox"
        expect(isArmableField(input)).toBe(false)
    })

    it("rejects a non-field element", () => {
        expect(isArmableField(document.createElement("div"))).toBe(false)
    })

    it("rejects null", () => {
        expect(isArmableField(null)).toBe(false)
    })

    it("rejects Monaco's internal input textarea", () => {
        const monacoRoot = document.createElement("div")
        monacoRoot.className = "monaco-editor"
        const textarea = document.createElement("textarea")
        monacoRoot.appendChild(textarea)

        expect(isArmableField(textarea)).toBe(false)
    })

    it("rejects the chip search filter input", () => {
        const filter = document.createElement("div")
        filter.className = "task-edit-data-filter"
        const input = document.createElement("input")
        filter.appendChild(input)

        expect(isArmableField(input)).toBe(false)
    })
})
