import {describe, it, expect, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"
import {useUnsavedChangesStore} from "./unsavedChanges"

describe("useUnsavedChangesStore", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    it("starts with dialog hidden and unsavedChange set to false", () => {
        const store = useUnsavedChangesStore()

        expect(store.isDialogVisible).toBe(false)
        expect(store.unsavedChange).toBe(false)
    })

    it("resolves with true and hides dialog on handleLeave", async () => {
        const store = useUnsavedChangesStore()

        const promise = store.showDialog()
        expect(store.isDialogVisible).toBe(true)

        store.handleLeave()

        const result = await promise
        expect(result).toBe(true)
        expect(store.isDialogVisible).toBe(false)
    })

    it("resolves with false and hides dialog on handleCancel", async () => {
        const store = useUnsavedChangesStore()

        const promise = store.showDialog()
        expect(store.isDialogVisible).toBe(true)

        store.handleCancel()

        const result = await promise
        expect(result).toBe(false)
        expect(store.isDialogVisible).toBe(false)
    })

    it("clears stored callback so repeated handleLeave or handleCancel calls do not throw", async () => {
        const store = useUnsavedChangesStore()

        const promise = store.showDialog()
        store.handleLeave()
        await promise

        expect(() => store.handleLeave()).not.toThrow()
        expect(() => store.handleCancel()).not.toThrow()
        expect(store.isDialogVisible).toBe(false)
    })
})
