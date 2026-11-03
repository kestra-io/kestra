import {afterEach, describe, expect, it} from "vitest"
import {useKeyShortcuts} from "./useKeyShortcuts"

describe("useKeyShortcuts", () => {
    afterEach(() => {
        const {hideKeyShortcuts} = useKeyShortcuts()
        hideKeyShortcuts()
    })

    it("starts with isKeyShortcutsDialogShown as false", () => {
        const {isKeyShortcutsDialogShown} = useKeyShortcuts()
        expect(isKeyShortcutsDialogShown.value).toBe(false)
    })

    it("sets isKeyShortcutsDialogShown to true when showKeyShortcuts is called", () => {
        const {isKeyShortcutsDialogShown, showKeyShortcuts} = useKeyShortcuts()

        showKeyShortcuts()

        expect(isKeyShortcutsDialogShown.value).toBe(true)
    })

    it("resets isKeyShortcutsDialogShown to false when hideKeyShortcuts is called", () => {
        const {isKeyShortcutsDialogShown, showKeyShortcuts, hideKeyShortcuts} = useKeyShortcuts()

        showKeyShortcuts()
        expect(isKeyShortcutsDialogShown.value).toBe(true)

        hideKeyShortcuts()
        expect(isKeyShortcutsDialogShown.value).toBe(false)
    })

    it("shares state across separate useKeyShortcuts invocations", () => {
        const first = useKeyShortcuts()
        const second = useKeyShortcuts()

        expect(first.isKeyShortcutsDialogShown.value).toBe(false)
        expect(second.isKeyShortcutsDialogShown.value).toBe(false)

        first.showKeyShortcuts()

        expect(first.isKeyShortcutsDialogShown.value).toBe(true)
        expect(second.isKeyShortcutsDialogShown.value).toBe(true)

        second.hideKeyShortcuts()

        expect(first.isKeyShortcutsDialogShown.value).toBe(false)
        expect(second.isKeyShortcutsDialogShown.value).toBe(false)
    })

    it("leaves the flag true on repeated calls to showKeyShortcuts without toggling", () => {
        const {isKeyShortcutsDialogShown, showKeyShortcuts} = useKeyShortcuts()

        showKeyShortcuts()
        expect(isKeyShortcutsDialogShown.value).toBe(true)

        showKeyShortcuts()
        expect(isKeyShortcutsDialogShown.value).toBe(true)
    })

    it("leaves the flag false on repeated calls to hideKeyShortcuts without toggling", () => {
        const {isKeyShortcutsDialogShown, hideKeyShortcuts} = useKeyShortcuts()

        hideKeyShortcuts()
        expect(isKeyShortcutsDialogShown.value).toBe(false)

        hideKeyShortcuts()
        expect(isKeyShortcutsDialogShown.value).toBe(false)
    })
})
