import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {type Directive, nextTick} from "vue"
import {createI18n} from "vue-i18n"

const flowStore = vi.hoisted(() => ({
    flowYaml: "id: parent\nnamespace: company.team\ntasks: []\n",
    flowGraph: {nodes: [], edges: []},
    invalidGraph: false,
    flow: {id: "parent", namespace: "company.team"},
    expandedSubflows: [] as string[],
    isAllowedEdit: true,
    isReadOnly: false,
    fetchGraph: vi.fn<() => Promise<void>>(),
    onEdit: vi.fn(),
    loadGraphFromSource: vi.fn(),
}))
const toastError = vi.hoisted(() => vi.fn())

vi.mock("../../stores/flow", () => ({
    useFlowStore: () => flowStore,
}))

vi.mock("../../utils/toast", () => ({
    useToast: () => ({error: toastError}),
}))

vi.mock("./LowCodeEditor.vue", () => ({
    default: {
        name: "LowCodeEditor",
        emits: ["expand-subflow"],
        template: "<button data-test='expand' @click=\"$emit('expand-subflow', ['child'])\" />",
    },
}))

import LowCodeEditorWrapper from "./LowCodeEditorWrapper.vue"

const i18n = createI18n({
    legacy: false,
    globalInjection: true,
    locale: "en",
    messages: {
        en: {
            "topology-graph": {
                load_error: "Could not load the topology graph. Please try again.",
            },
        },
    },
})

const loadingDirective: Directive<HTMLElement, boolean> = {
    mounted(element, binding) {
        element.dataset.loading = String(binding.value)
    },
    updated(element, binding) {
        element.dataset.loading = String(binding.value)
    },
}

function mountWrapper() {
    return mount(LowCodeEditorWrapper, {
        global: {
            plugins: [i18n],
            directives: {"ks-loading": loadingDirective},
            stubs: {KsAlert: true},
        },
    })
}

describe("LowCodeEditorWrapper", () => {
    beforeEach(() => {
        flowStore.expandedSubflows = []
        flowStore.fetchGraph.mockReset()
        toastError.mockReset()
        vi.spyOn(console, "error").mockImplementation(() => {})
    })

    afterEach(() => {
        vi.restoreAllMocks()
    })

    it("should refetch the graph when a subflow is expanded", async () => {
        let resolveFetch = () => {}
        flowStore.fetchGraph.mockImplementation(() => new Promise<void>(resolve => {
            resolveFetch = resolve
        }))

        const wrapper = mountWrapper()

        await wrapper.get("[data-test='expand']").trigger("click")
        await nextTick()

        expect(flowStore.expandedSubflows).toEqual(["child"])
        expect(flowStore.fetchGraph).toHaveBeenCalledOnce()
        expect(wrapper.get("#topologyWrapper").attributes("data-loading")).toBe("true")

        resolveFetch()
        await flushPromises()

        expect(wrapper.get("#topologyWrapper").attributes("data-loading")).toBe("false")
    })

    it("should restore expanded subflows and show an error when the graph request fails", async () => {
        flowStore.expandedSubflows = ["existing"]
        flowStore.fetchGraph.mockRejectedValueOnce({status: 500})
        const wrapper = mountWrapper()

        await wrapper.get("[data-test='expand']").trigger("click")
        await flushPromises()

        expect(flowStore.expandedSubflows).toEqual(["existing"])
        expect(toastError).toHaveBeenCalledWith("Could not load the topology graph. Please try again.")
        expect(wrapper.get("#topologyWrapper").attributes("data-loading")).toBe("false")
    })

    it("should not duplicate the store error when expanding a missing subflow", async () => {
        flowStore.fetchGraph.mockRejectedValueOnce({status: 404})
        const wrapper = mountWrapper()

        await wrapper.get("[data-test='expand']").trigger("click")
        await flushPromises()

        expect(flowStore.expandedSubflows).toEqual([])
        expect(toastError).not.toHaveBeenCalled()
    })
})
