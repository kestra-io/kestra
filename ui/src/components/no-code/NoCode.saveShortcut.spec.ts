import {afterEach, describe, expect, it, vi} from "vitest"
import {computed, defineComponent, h, inject} from "vue"
import {mount} from "@vue/test-utils"

const {flowStore, pluginsStore, injected, saveAsDraft} = vi.hoisted(() => ({
    flowStore: {} as Record<string, unknown>,
    pluginsStore: {} as Record<string, unknown>,
    injected: {} as {saveFlow?: () => void},
    saveAsDraft: vi.fn(),
}))

vi.mock("../../stores/flow", () => ({useFlowStore: () => flowStore}))
vi.mock("../../stores/plugins", () => ({usePluginsStore: () => pluginsStore}))
vi.mock("./utils/useKeyboardSave", () => ({useKeyboardSave: () => undefined}))
vi.mock("../flows/useFlowEditorActions", () => ({useFlowEditorActions: () => ({saveAsDraft})}))
vi.mock("./utils/useFlowFields", async (importOriginal) => ({
    ...await importOriginal<typeof import("./utils/useFlowFields")>(),
    useFlowFields: () => ({fieldsFromSchemaTop: computed(() => []), fieldsFromSchemaRest: computed(() => [])}),
}))
vi.mock("./utils/useFieldValidationErrors", async (importOriginal) => ({
    ...await importOriginal<typeof import("./utils/useFieldValidationErrors")>(),
    useFieldValidationErrors: () => computed(() => []),
}))

import NoCode from "./NoCode.vue"
import {SAVE_FLOW_FUNCTION_INJECTION_KEY} from "./injectionKeys"

const TaskProbe = defineComponent({
    setup() {
        injected.saveFlow = inject(SAVE_FLOW_FUNCTION_INJECTION_KEY)
        return () => h("div")
    },
})

afterEach(() => {
    vi.clearAllMocks()
})

describe("NoCode save shortcut provided to fields", () => {
    it("routes a field's save shortcut to the editor's draft save", () => {
        flowStore.flow = {id: "f", namespace: "ns", draft: false}
        flowStore.flowYaml = "id: f"
        pluginsStore.flowSchema = {}

        mount(NoCode, {
            props: {creatingTask: true},
            global: {stubs: {Task: TaskProbe, Wrapper: true, TaskObjectField: true, KsForm: true}},
        })
        injected.saveFlow?.()

        expect(saveAsDraft).toHaveBeenCalledOnce()
    })
})
