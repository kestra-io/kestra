import {describe, test, expect, vi, beforeEach, afterAll} from "vitest"
import {reactive} from "vue"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../tests/unit/i18nMount"

import {createPinia, setActivePinia} from "pinia"
import {useFlowStore} from "../../stores/flow"
import {useUnsavedChangesStore} from "../../stores/unsavedChanges"
import {COPILOT_DRAFT_KEY, RECIPE_PRESET_KEY} from "../../utils/storageKeys"
import {storageKeys} from "../../utils/constants"
import {useMiscStore} from "../../override/stores/misc"

const route = reactive({params: {} as Record<string, string>, query: {} as Record<string, string>})

vi.mock("vue-router", () => ({
    useRoute: () => route,
    useRouter: () => ({push: vi.fn(), replace: vi.fn()}),
}))

import FlowCreate from "./FlowCreate.vue"

let pinia: ReturnType<typeof createPinia>
let flowStore: ReturnType<typeof useFlowStore>

const mountCreate = () => i18nMount(FlowCreate, {
    global: {
        plugins: [pinia],
        stubs: {
            TopNavBar: {template: "<div><slot name='actions' /></div>"},
            Actions: true,
            MultiPanelFlowEditorView: {template: "<div data-test='editor' />"},
        },
    },
})

beforeEach(() => {
    route.params = {}
    route.query = {}
    sessionStorage.clear()
    localStorage.clear()

    pinia = createPinia()
    setActivePinia(pinia)
    flowStore = useFlowStore()
    vi.spyOn(flowStore, "initYamlSource").mockResolvedValue(undefined)
})

const documentTitle = document.title

afterAll(() => {
    localStorage.clear()
    sessionStorage.clear()
    document.title = documentTitle
})

describe("FlowCreate", () => {
    test("opens the editor on a starter flow without asking anything first", async () => {
        // Given / When
        const wrapper = mountCreate()
        await flushPromises()

        // Then
        expect(wrapper.find("[data-test='editor']").exists()).toBe(true)
        expect(flowStore.flow?.id).toBeTruthy()
        expect(flowStore.flow?.source).toContain(`id: ${flowStore.flow?.id}`)
        expect(flowStore.flow?.source).toContain("type: io.kestra.plugin.core.log.Log")
    })

    test("uses the namespace carried by the create link", async () => {
        // Given
        route.query = {namespace: "company.analytics"}

        // When
        mountCreate()
        await flushPromises()

        // Then
        expect(flowStore.flow?.namespace).toBe("company.analytics")
        expect(flowStore.flow?.source).toContain("namespace: company.analytics")
    })

    test.each(["copy", "onboardingPreset", "recipePreset", "ai", "createTrigger"])(
        "still opens the editor for ?%s",
        async (key) => {
            // Given
            route.query = {[key]: "true"}

            // When
            const wrapper = mountCreate()
            await flushPromises()

            // Then
            expect(wrapper.find("[data-test='editor']").exists()).toBe(true)
            expect(flowStore.flow).toBeDefined()
        },
    )
})

describe("FlowCreate recipe hand-off", () => {
    test("consumes the recipe preset from sessionStorage exactly once", async () => {
        // Given
        route.query = {recipePreset: "true"}
        sessionStorage.setItem(RECIPE_PRESET_KEY, "id: system-flow-alert\nnamespace: system\n")

        // When
        mountCreate()
        await flushPromises()

        // Then
        expect(flowStore.flow?.source).toContain("system-flow-alert")
        expect(sessionStorage.getItem(RECIPE_PRESET_KEY)).toBeNull()
    })
})

describe("FlowCreate copilot hand-off", () => {
    test("consumes the copilot draft from sessionStorage exactly once", async () => {
        // Given
        route.query = {copilotDraft: "true"}
        sessionStorage.setItem(COPILOT_DRAFT_KEY, "id: copilot-flow\nnamespace: company.team\n")

        // When
        mountCreate()
        await flushPromises()

        // Then
        expect(flowStore.flow?.id).toBe("copilot-flow")
        expect(flowStore.flow?.namespace).toBe("company.team")
        expect(sessionStorage.getItem(COPILOT_DRAFT_KEY)).toBeNull()
    })

    test("loads a draft handed over while the create page is already open", async () => {
        // Given
        mountCreate()
        await flushPromises()
        const starterId = flowStore.flow?.id
        sessionStorage.setItem(COPILOT_DRAFT_KEY, "id: copilot-flow\nnamespace: company.team\n")

        // When
        route.query = {copilotDraft: "1730000000000"}
        await flushPromises()

        // Then
        expect(starterId).not.toBe("copilot-flow")
        expect(flowStore.flow?.id).toBe("copilot-flow")
        expect(sessionStorage.getItem(COPILOT_DRAFT_KEY)).toBeNull()
    })

    test.each([
        {choice: "leaves", leave: true, expectedId: "copilot-flow"},
        {choice: "stays", leave: false, expectedId: "starter-flow"},
    ])("asks before replacing unsaved edits and the user $choice", async ({leave, expectedId}) => {
        // Given
        mountCreate()
        await flushPromises()
        flowStore.flow = {id: "starter-flow", namespace: "company.team", source: "id: starter-flow\nnamespace: company.team"}
        const unsavedChangesStore = useUnsavedChangesStore()
        unsavedChangesStore.unsavedChange = true
        const showDialog = vi.spyOn(unsavedChangesStore, "showDialog").mockResolvedValue(leave)
        sessionStorage.setItem(COPILOT_DRAFT_KEY, "id: copilot-flow\nnamespace: company.team\n")

        // When
        route.query = {copilotDraft: "1730000000000"}
        await flushPromises()

        // Then
        expect(showDialog).toHaveBeenCalledOnce()
        expect(flowStore.flow?.id).toBe(expectedId)
        expect(sessionStorage.getItem(COPILOT_DRAFT_KEY)).toBeNull()
    })

    test("falls back to the default template when the draft is gone after a refresh", async () => {
        // Given
        route.query = {copilotDraft: "true"}

        // When
        const wrapper = mountCreate()
        await flushPromises()

        // Then
        expect(wrapper.find("[data-test='editor']").exists()).toBe(true)
        expect(flowStore.flow?.source).toBeTruthy()
    })
})

describe("FlowCreate source hand-off", () => {
    test("opens the YAML carried by the create link without a blueprint id", async () => {
        // Given
        route.query = {blueprintSourceYaml: "id: ttl_expiring\nnamespace: system\n"}

        // When
        mountCreate()
        await flushPromises()

        // Then
        expect(flowStore.flow?.id).toBe("ttl_expiring")
        expect(flowStore.flow?.namespace).toBe("system")
    })
})

describe("FlowCreate default template", () => {
    const INSTANCE_TEMPLATE = "tasks:\n  - id: configured\n    type: io.kestra.plugin.core.log.Log"
    const USER_TEMPLATE = "labels:\n  owner: Thibault\ntasks:\n  - id: mine\n    type: io.kestra.plugin.core.log.Log"

    const openBlankFlow = async () => {
        const wrapper = mountCreate()
        await flushPromises()
        return wrapper
    }

    test("uses the built-in template when neither the user nor the instance configured one", async () => {
        // Given / When
        await openBlankFlow()

        // Then
        expect(flowStore.flow?.source).toContain("io.kestra.plugin.core.log.Log")
        expect(flowStore.flow?.source).toContain("message: Hello World!")
    })

    test("uses the instance template over the built-in one", async () => {
        // Given
        useMiscStore().configs = {flowTemplate: INSTANCE_TEMPLATE}

        // When
        await openBlankFlow()

        // Then
        expect(flowStore.flow?.source).toContain("id: configured")
        expect(flowStore.flow?.source).not.toContain("Hello World!")
    })

    test("uses the template saved by the user over the instance one", async () => {
        // Given
        useMiscStore().configs = {flowTemplate: INSTANCE_TEMPLATE}
        localStorage.setItem(storageKeys.FLOW_TEMPLATE, USER_TEMPLATE)

        // When
        await openBlankFlow()

        // Then
        expect(flowStore.flow?.source).toContain("owner: Thibault")
        expect(flowStore.flow?.source).not.toContain("id: configured")
    })

    test("still generates the id and namespace missing from the user template", async () => {
        // Given
        localStorage.setItem(storageKeys.FLOW_TEMPLATE, USER_TEMPLATE)

        // When
        await openBlankFlow()

        // Then
        expect(flowStore.flow?.source).toContain(`id: ${flowStore.flow?.id}`)
        expect(flowStore.flow?.source).toContain("namespace: company.team")
        expect(flowStore.flow?.source).toContain("owner: Thibault")
    })

    test("keeps the id and namespace the user template sets itself", async () => {
        // Given
        localStorage.setItem(storageKeys.FLOW_TEMPLATE, "id: pinned\nnamespace: company.pinned\ntasks: []")

        // When
        await openBlankFlow()

        // Then
        expect(flowStore.flow?.id).toBe("pinned")
        expect(flowStore.flow?.source).toContain("id: pinned")
        expect(flowStore.flow?.source.match(/^id:/gm)).toHaveLength(1)
    })

    test("falls back to the instance template when the user cleared theirs", async () => {
        // Given
        useMiscStore().configs = {flowTemplate: INSTANCE_TEMPLATE}
        localStorage.setItem(storageKeys.FLOW_TEMPLATE, "")

        // When
        await openBlankFlow()

        // Then
        expect(flowStore.flow?.source).toContain("id: configured")
    })
})
