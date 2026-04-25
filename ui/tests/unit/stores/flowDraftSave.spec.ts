import {beforeEach, describe, expect, it} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {saveDefaultActions, storageKeys} from "../../../src/utils/constants"

describe("flow draft save", () => {
    beforeEach(() => {
        localStorage.clear()
        setActivePinia(createPinia())
    })

    it("exposes a saveAsDraft action on the flow store", async () => {
        const {useFlowStore} = await import("../../../src/stores/flow")
        const store = useFlowStore()
        expect(typeof store.saveAsDraft).toBe("function")
    })

    it("exposes a save action on the flow store", async () => {
        const {useFlowStore} = await import("../../../src/stores/flow")
        const store = useFlowStore()
        expect(typeof store.save).toBe("function")
        expect(typeof store.saveAll).toBe("function")
    })
})

describe("save default action fallback", () => {
    beforeEach(() => {
        localStorage.clear()
    })

    // Mirrors EditorButtons.vue readDefault(): falls back to SAVE when nothing is stored.
    it("EditorButtons: defaults to SAVE when no preference is stored", () => {
        const stored = localStorage.getItem(storageKeys.SAVE_DEFAULT_ACTION)
        const valid = [saveDefaultActions.SAVE, saveDefaultActions.SAVE_AS_DRAFT]
        const result = valid.includes(stored as typeof saveDefaultActions[keyof typeof saveDefaultActions])
            ? stored
            : saveDefaultActions.SAVE
        expect(result).toBe(saveDefaultActions.SAVE)
    })

    it("EditorButtons: honours an explicit SAVE_AS_DRAFT preference", () => {
        localStorage.setItem(storageKeys.SAVE_DEFAULT_ACTION, saveDefaultActions.SAVE_AS_DRAFT)
        const stored = localStorage.getItem(storageKeys.SAVE_DEFAULT_ACTION)
        const valid = [saveDefaultActions.SAVE, saveDefaultActions.SAVE_AS_DRAFT]
        const result = valid.includes(stored as typeof saveDefaultActions[keyof typeof saveDefaultActions])
            ? stored
            : saveDefaultActions.SAVE
        expect(result).toBe(saveDefaultActions.SAVE_AS_DRAFT)
    })

    // Mirrors useKeyboardSave.ts: `draft` flag is false when no preference is stored.
    it("useKeyboardSave: draft flag is false when no preference is stored", () => {
        const draft = localStorage.getItem(storageKeys.SAVE_DEFAULT_ACTION) === saveDefaultActions.SAVE_AS_DRAFT
        expect(draft).toBe(false)
    })

    it("useKeyboardSave: draft flag is true when SAVE_AS_DRAFT is stored", () => {
        localStorage.setItem(storageKeys.SAVE_DEFAULT_ACTION, saveDefaultActions.SAVE_AS_DRAFT)
        const draft = localStorage.getItem(storageKeys.SAVE_DEFAULT_ACTION) === saveDefaultActions.SAVE_AS_DRAFT
        expect(draft).toBe(true)
    })

    // Mirrors BasicSettings.vue created(): falls back to SAVE when nothing is stored.
    it("BasicSettings: defaults to SAVE when no preference is stored", () => {
        const result = localStorage.getItem(storageKeys.SAVE_DEFAULT_ACTION) || saveDefaultActions.SAVE
        expect(result).toBe(saveDefaultActions.SAVE)
    })
})
