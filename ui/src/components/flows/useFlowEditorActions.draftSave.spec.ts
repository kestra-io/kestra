import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

const saveAll = vi.fn()

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}}),
    useRouter: () => ({push: vi.fn()}),
}))
vi.mock("vue-i18n", () => ({
    useI18n: () => ({t: (key: string) => key}),
}))
vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {}}),
}))
vi.mock("../../stores/flow", async (importOriginal) => ({
    ...await importOriginal<typeof import("../../stores/flow")>(),
    useFlowStore: () => ({flow: {draft: true}, isCreating: false, saveAll}),
}))

describe("useFlowEditorActions — saving a draft flow", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        saveAll.mockReset()
    })

    it("keeps the draft state on the keyboard save", async () => {
        const {useFlowEditorActions} = await import("./useFlowEditorActions")

        await useFlowEditorActions().saveKeepingDraftState()

        expect(saveAll).toHaveBeenCalledExactlyOnceWith(undefined)
    })

    it("publishes on the Save menu item, the explicit way out of draft mode", async () => {
        const {useFlowEditorActions} = await import("./useFlowEditorActions")

        await useFlowEditorActions().save()

        expect(saveAll).toHaveBeenCalledExactlyOnceWith(false)
    })
})
