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

    it("toggles unsavedChange flag correctly", () => {
        const store = useUnsavedChangesStore()

        store.unsavedChange = true
        expect(store.unsavedChange).toBe(true)

        store.unsavedChange = false
        expect(store.unsavedChange).toBe(false)
    })

    it("shows dialog and returns a pending promise on showDialog", async () => {
        const store = useUnsavedChangesStore()

        let resolved = false
        const promise = store.showDialog().then((val) => {
            resolved = true
            return val
        })

        expect(store.isDialogVisible).toBe(true)
        expect(resolved).toBe(false)

        store.handleLeave()
        const result = await promise
        expect(result).toBe(true)
        expect(resolved).toBe(true)
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

    it("shares state across multiple store consumers within the same Pinia context", () => {
        const consumerA = useUnsavedChangesStore()
        const consumerB = useUnsavedChangesStore()

        consumerA.unsavedChange = true
        expect(consumerB.unsavedChange).toBe(true)

        consumerA.showDialog()
        expect(consumerB.isDialogVisible).toBe(true)

        consumerB.handleCancel()
        expect(consumerA.isDialogVisible).toBe(false)
    })
})
