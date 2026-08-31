import {describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {type Directive, nextTick} from "vue"

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

vi.mock("../../stores/flow", () => ({
    useFlowStore: () => flowStore,
}))

vi.mock("./LowCodeEditor.vue", () => ({
    default: {
        name: "LowCodeEditor",
        emits: ["expand-subflow"],
        template: "<button data-test='expand' @click=\"$emit('expand-subflow', ['child'])\" />",
    },
}))

import LowCodeEditorWrapper from "./LowCodeEditorWrapper.vue"

const loadingDirective: Directive<HTMLElement, boolean> = {
    mounted(element, binding) {
        element.dataset.loading = String(binding.value)
    },
    updated(element, binding) {
        element.dataset.loading = String(binding.value)
    },
}

describe("LowCodeEditorWrapper", () => {
    it("should refetch the graph when a subflow is expanded", async () => {
        let resolveFetch = () => {}
        flowStore.expandedSubflows = []
        flowStore.fetchGraph.mockReset()
        flowStore.fetchGraph.mockImplementation(() => new Promise<void>(resolve => {
            resolveFetch = resolve
        }))

        const wrapper = mount(LowCodeEditorWrapper, {
            global: {
                directives: {"ks-loading": loadingDirective},
                stubs: {KsAlert: true},
            },
        })

        await wrapper.get("[data-test='expand']").trigger("click")
        await nextTick()

        expect(flowStore.expandedSubflows).toEqual(["child"])
        expect(flowStore.fetchGraph).toHaveBeenCalledOnce()
        expect(wrapper.get("#topologyWrapper").attributes("data-loading")).toBe("true")

        resolveFetch()
        await flushPromises()

        expect(wrapper.get("#topologyWrapper").attributes("data-loading")).toBe("false")
    })
})
