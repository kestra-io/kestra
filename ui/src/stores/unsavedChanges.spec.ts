import {beforeEach, describe, expect, it} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {useUnsavedChangesStore} from "./unsavedChanges"

describe("unsavedChanges store", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    it("starts with the dialog hidden and unsavedChange as false", () => {
        const store = useUnsavedChangesStore()

        expect(store.isDialogVisible).toBe(false)
        expect(store.unsavedChange).toBe(false)
    })

    it("makes the dialog visible and returns a pending promise when showDialog is called", () => {
        const store = useUnsavedChangesStore()

        let resolved = false
        const promise = store.showDialog().then((val) => {
            resolved = true
            return val
        })

        expect(store.isDialogVisible).toBe(true)
        expect(resolved).toBe(false)
        expect(promise).toBeInstanceOf(Promise)
    })

    it("resolves the pending promise with true and hides the dialog on handleLeave", async () => {
        const store = useUnsavedChangesStore()

        const promise = store.showDialog()
        expect(store.isDialogVisible).toBe(true)

        store.handleLeave()

        expect(store.isDialogVisible).toBe(false)
        const result = await promise
        expect(result).toBe(true)
    })

    it("resolves the pending promise with false and hides the dialog on handleCancel", async () => {
        const store = useUnsavedChangesStore()

        const promise = store.showDialog()
        expect(store.isDialogVisible).toBe(true)

        store.handleCancel()

        expect(store.isDialogVisible).toBe(false)
        const result = await promise
        expect(result).toBe(false)
    })

    it("clears the stored callback after resolving so a second call does not throw", async () => {
        const store = useUnsavedChangesStore()

        const promise = store.showDialog()
        store.handleLeave()
        await promise

        // Second invocation when resolveCallback is null
        expect(() => store.handleLeave()).not.toThrow()
        expect(() => store.handleCancel()).not.toThrow()
        expect(store.isDialogVisible).toBe(false)
    })

    it("toggles unsavedChange properly", () => {
        const store = useUnsavedChangesStore()

        expect(store.unsavedChange).toBe(false)

        store.unsavedChange = true
        expect(store.unsavedChange).toBe(true)

        store.unsavedChange = false
        expect(store.unsavedChange).toBe(false)
    })
})
