import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

const flowState: {flowValidation?: {outdated?: boolean, errors?: {detail: string, path?: string}[]}, flowErrors?: string[]} = {}

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
    useFlowStore: () => ({isCreating: true, ...flowState}),
}))

describe("useFlowEditorActions — errors that block the main save action", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    it("does not count the already-exists notice of a create, whose remedy is to save", async () => {
        const {useFlowEditorActions} = await import("./useFlowEditorActions")

        flowState.flowValidation = {outdated: true}
        flowState.flowErrors = ["A flow with the same id already exists in this namespace."]
        expect(useFlowEditorActions().hasErrors.value).toBe(true)
        expect(useFlowEditorActions().hasBlockingErrors.value).toBe(false)

        flowState.flowValidation = {outdated: true, errors: [{detail: "must not be null", path: "tasks[log].message"}]}
        expect(useFlowEditorActions().hasBlockingErrors.value).toBe(true)
    })
})
