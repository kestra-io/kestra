import {describe, test, expect, vi, beforeEach, afterAll} from "vitest"
import {mount, flushPromises} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {createPinia, setActivePinia} from "pinia"
import {useFlowStore} from "../../../../src/stores/flow"
import {RECIPE_PRESET_KEY} from "../../../../src/utils/storageKeys"
import {storageKeys} from "../../../../src/utils/constants"
import {useMiscStore} from "../../../../src/override/stores/misc"

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

describe("FlowCreate default template", () => {
    const INSTANCE_TEMPLATE = "tasks:\n  - id: configured\n    type: io.kestra.plugin.core.log.Log"
    const USER_TEMPLATE = "labels:\n  owner: Thibault\ntasks:\n  - id: mine\n    type: io.kestra.plugin.core.log.Log"

    const openBlankFlow = async () => {
        const wrapper = mountCreate()
        wrapper.findComponent({name: "NewFlowLanding"}).vm.$emit("proceed", {id: "my-flow", namespace: "company.team"})
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
        expect(flowStore.flow?.source).toContain("id: my-flow")
        expect(flowStore.flow?.source).toContain("namespace: company.team")
        expect(flowStore.flow?.source).toContain("owner: Thibault")
    })

    test("keeps the id and namespace the user template sets itself", async () => {
        // Given
        localStorage.setItem(storageKeys.FLOW_TEMPLATE, "id: pinned\nnamespace: company.pinned\ntasks: []")

        // When
        await openBlankFlow()

        // Then
        expect(flowStore.flow?.source).toContain("id: pinned")
        expect(flowStore.flow?.source).not.toContain("id: my-flow")
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
