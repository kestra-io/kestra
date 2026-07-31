import {describe, test, expect, vi, beforeEach} from "vitest"
import {mount, flushPromises} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {createPinia} from "pinia"
import {RECIPE_PRESET_KEY} from "../../../../src/utils/storageKeys"

const route = {params: {} as Record<string, string>, query: {} as Record<string, string>}

const flowStore = {flow: undefined as Record<string, unknown> | undefined, flowValidation: undefined, isCreating: false, initYamlSource: vi.fn()}

vi.mock("vue-router", () => ({useRoute: () => route}))
vi.mock("override/stores/misc", () => ({useMiscStore: () => ({configs: {}})}))
vi.mock("override/stores/auth", () => ({useAuthStore: () => ({user: {getNamespacesForAction: () => []}})}))
vi.mock("../../../../src/stores/flow", () => ({useFlowStore: () => flowStore}))
vi.mock("../../../../src/stores/blueprints", () => ({
    useBlueprintsStore: () => ({
        getBlueprintSource: vi.fn().mockResolvedValue("id: from-catalog\nnamespace: company.team\n"),
        getFlowBlueprint: vi.fn().mockResolvedValue({source: "id: from-blueprint\nnamespace: company.team\n"}),
    }),
}))
vi.mock("../../../../src/composables/useNamespaces", () => ({default: () => ({all: vi.fn().mockResolvedValue([])}), defaultNamespace: () => undefined}))
vi.mock("../../../../src/composables/useRouteContext", () => ({default: vi.fn()}))
vi.mock("../../../../src/utils/id", () => ({getRandomID: () => "generated-id"}))

const globalConfig = {
    global: {
        plugins: [createI18n({legacy: false, locale: "en", messages: {en: {flows: "Flows"}}}), createPinia()],
        stubs: {
            TopNavBar: {template: "<div><slot name='actions' /></div>"},
            Actions: true,
            MultiPanelFlowEditorView: {template: "<div data-test='editor' />"},
            NewFlowLanding: {name: "NewFlowLanding", template: "<div data-test='landing' />", emits: ["proceed", "import"]},
            ImportYaml: {name: "ImportYaml", template: "<div data-test='import' />", emits: ["submit", "back"]},
        },
    },
}

import FlowCreate from "../../../../src/components/flows/FlowCreate.vue"

const mountCreate = () => mount(FlowCreate, globalConfig)

describe("FlowCreate landing gate", () => {
    beforeEach(() => {
        route.params = {}
        route.query = {}
        flowStore.flow = undefined
        flowStore.initYamlSource.mockClear()
        sessionStorage.clear()
    })

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

    test.each(["copy", "blueprintId", "onboardingPreset", "recipePreset", "ai", "createTrigger"])(
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
    beforeEach(() => {
        route.params = {}
        route.query = {}
        flowStore.flow = undefined
        sessionStorage.clear()
    })

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
        expect(flowStore.flow?.source).toContain("id: generated-id")
        expect(flowStore.flow?.source).toContain("namespace: company.team")
        expect(flowStore.flow?.source).toContain("type: io.kestra.plugin.core.log.Log")
    })
})

describe("FlowCreate recipe hand-off", () => {
    beforeEach(() => {
        route.params = {}
        route.query = {}
        flowStore.flow = undefined
        sessionStorage.clear()
    })

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
