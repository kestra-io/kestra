import {describe, test, expect, vi, beforeEach, afterAll} from "vitest"
import {mount, flushPromises} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {createPinia, setActivePinia} from "pinia"
import {useFlowStore} from "../../../../src/stores/flow"
import {RECIPE_PRESET_KEY} from "../../../../src/utils/storageKeys"

const route = {params: {} as Record<string, string>, query: {} as Record<string, string>}

vi.mock("vue-router", () => ({
    useRoute: () => route,
    useRouter: () => ({push: vi.fn(), replace: vi.fn()}),
}))

import FlowCreate from "../../../../src/components/flows/FlowCreate.vue"

const i18n = createI18n({legacy: false, locale: "en", missingWarn: false, fallbackWarn: false, messages: {en: {}}})

let pinia: ReturnType<typeof createPinia>
let flowStore: ReturnType<typeof useFlowStore>

const mountCreate = () => mount(FlowCreate, {
    global: {
        plugins: [i18n, pinia],
        stubs: {
            TopNavBar: {template: "<div><slot name='actions' /></div>"},
            Actions: true,
            MultiPanelFlowEditorView: {template: "<div data-test='editor' />"},
            NewFlowLanding: {name: "NewFlowLanding", template: "<div data-test='landing' />", emits: ["proceed", "import"]},
            ImportYaml: {name: "ImportYaml", template: "<div data-test='import' />", emits: ["submit", "back"]},
        },
    },
})

beforeEach(() => {
    route.params = {}
    route.query = {}
    sessionStorage.clear()

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

describe("FlowCreate landing gate", () => {
    test("shows the funnel and touches no flow when opened without context", () => {
        // Given / When
        const wrapper = mountCreate()

        // Then
        expect(wrapper.find("[data-test='landing']").exists()).toBe(true)
        expect(wrapper.find("[data-test='editor']").exists()).toBe(false)
        expect(flowStore.flow).toBeUndefined()
    })

    test("shows the funnel for a namespace-scoped create link", () => {
        // Given
        route.query = {namespace: "company.team"}

        // When / Then
        expect(mountCreate().find("[data-test='landing']").exists()).toBe(true)
    })

    test.each(["copy", "onboardingPreset", "recipePreset", "ai", "createTrigger"])(
        "goes straight to the editor for ?%s",
        async (key) => {
            // Given
            route.query = {[key]: "true"}

            // When
            const wrapper = mountCreate()
            await flushPromises()

            // Then
            expect(wrapper.find("[data-test='landing']").exists()).toBe(false)
            expect(flowStore.flow).toBeDefined()
        },
    )
})

describe("FlowCreate hand-off from the funnel", () => {
    test("opens the editor on the id and namespace chosen in the blank-flow form", async () => {
        // Given
        const wrapper = mountCreate()

        // When
        wrapper.findComponent({name: "NewFlowLanding"}).vm.$emit("proceed", {id: "my-flow", namespace: "company.analytics"})
        await flushPromises()

        // Then
        expect(flowStore.flow?.id).toBe("my-flow")
        expect(flowStore.flow?.namespace).toBe("company.analytics")
        expect(flowStore.flow?.source).toContain("id: my-flow")
        expect(wrapper.find("[data-test='editor']").exists()).toBe(true)
    })

    test("keeps the pasted definition intact when it already carries its metadata", async () => {
        // Given
        const wrapper = mountCreate()
        const yaml = "id: pasted-flow\nnamespace: company.team\ntasks:\n  - id: hello\n    type: io.kestra.plugin.core.log.Log\n"

        // When
        wrapper.findComponent({name: "NewFlowLanding"}).vm.$emit("import")
        await flushPromises()
        wrapper.findComponent({name: "ImportYaml"}).vm.$emit("submit", {yaml})
        await flushPromises()

        // Then
        expect(flowStore.flow?.id).toBe("pasted-flow")
        expect(flowStore.flow?.namespace).toBe("company.team")
        expect(flowStore.flow?.source).toBe(yaml)
    })

    test("adds the missing id and namespace to an imported fragment so it can be saved", async () => {
        // Given
        const wrapper = mountCreate()
        const fragment = "tasks:\n  - id: hello\n    type: io.kestra.plugin.core.log.Log\n"

        // When
        wrapper.findComponent({name: "NewFlowLanding"}).vm.$emit("import")
        await flushPromises()
        wrapper.findComponent({name: "ImportYaml"}).vm.$emit("submit", {yaml: fragment})
        await flushPromises()

        // Then
        expect(flowStore.flow?.source).toContain(`id: ${flowStore.flow?.id}`)
        expect(flowStore.flow?.source).toContain(`namespace: ${flowStore.flow?.namespace}`)
        expect(flowStore.flow?.source).toContain("type: io.kestra.plugin.core.log.Log")
    })
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
