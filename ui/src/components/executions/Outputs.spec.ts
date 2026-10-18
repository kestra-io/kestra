import {describe, it, expect, vi, beforeEach} from "vitest"
import {ref, nextTick} from "vue"
import {flushPromises} from "@vue/test-utils"
import {KsDropdownItem} from "@kestra-io/design-system"
import {i18nMount} from "../../../tests/unit/i18nMount"

const hasOutputsMock = ref(false)
const mockLoadTaskRunOutputs = vi.fn()

vi.mock("@kestra-io/design-system", async (importOriginal) => {
    const original = await importOriginal<Record<string, unknown>>()
    return {
        ...original,
        vKsLoading: {
            mounted(el: HTMLElement, binding: {value: boolean}) {
                el.dataset.loading = String(binding.value)
            },
            updated(el: HTMLElement, binding: {value: boolean}) {
                el.dataset.loading = String(binding.value)
            },
        },
    }
})

vi.mock("../../composables/useTaskRunOutputs", () => ({
    useHasTaskRunOutputs: () => hasOutputsMock,
    loadTaskRunOutputs: (...args: unknown[]) => mockLoadTaskRunOutputs(...args),
}))

import Outputs from "./Outputs.vue"

const stubs = {
    KsDropdownItem: {
        props: ["disabled", "icon"],
        template: "<button :disabled=\"disabled\"><slot /></button>",
    },
    KsDrawer: {
        props: ["modelValue", "title"],
        template: "<div data-test=\"drawer\"><slot /></div>",
    },
    Vars: {
        name: "Vars",
        props: ["execution", "data"],
        template: "<div data-test=\"vars\" :data-outputs=\"JSON.stringify(data)\" />",
    },
}

const defaultProps = {
    taskRun: {id: "task-456", state: {current: "SUCCESS"}},
    executionId: "exec-123",
    execution: {id: "exec-123"},
}

function mountOutputs(props = defaultProps) {
    return i18nMount(Outputs, {
        props,
        global: {
            stubs,
        },
    })
}

describe("Outputs.vue", () => {
    beforeEach(() => {
        vi.clearAllMocks()
        hasOutputsMock.value = false
    })

    it("the control is disabled when the task run has no outputs", () => {
        hasOutputsMock.value = false
        const wrapper = mountOutputs()

        const item = wrapper.findComponent(KsDropdownItem)
        expect(item.props("disabled")).toBe(true)
    })

    it("it is enabled when outputs exist", () => {
        hasOutputsMock.value = true
        const wrapper = mountOutputs()

        const item = wrapper.findComponent(KsDropdownItem)
        expect(item.props("disabled")).toBe(false)
    })

    it("opening the panel loads the outputs once", async () => {
        hasOutputsMock.value = true
        mockLoadTaskRunOutputs.mockResolvedValueOnce({key: "value1"})
        const wrapper = mountOutputs()

        await wrapper.findComponent(KsDropdownItem).trigger("click")
        await flushPromises()

        expect(mockLoadTaskRunOutputs).toHaveBeenCalledTimes(1)
        expect(mockLoadTaskRunOutputs).toHaveBeenCalledWith("exec-123", "task-456")
    })

    it("reopening does not fetch again", async () => {
        hasOutputsMock.value = true
        mockLoadTaskRunOutputs.mockResolvedValueOnce({key: "value1"})
        const wrapper = mountOutputs()

        // Open panel
        await wrapper.findComponent(KsDropdownItem).trigger("click")
        await flushPromises()
        expect(mockLoadTaskRunOutputs).toHaveBeenCalledTimes(1)

        // Close panel
        await wrapper.findComponent(KsDropdownItem).trigger("click")
        await flushPromises()

        // Reopen panel
        await wrapper.findComponent(KsDropdownItem).trigger("click")
        await flushPromises()
        expect(mockLoadTaskRunOutputs).toHaveBeenCalledTimes(1)
    })

    it("a loading state shows while the fetch is in flight", async () => {
        hasOutputsMock.value = true
        let resolvePromise!: (val: Record<string, unknown>) => void
        const pendingPromise = new Promise<Record<string, unknown>>((resolve) => {
            resolvePromise = resolve
        })
        mockLoadTaskRunOutputs.mockReturnValueOnce(pendingPromise)

        const wrapper = mountOutputs()
        await wrapper.findComponent(KsDropdownItem).trigger("click")
        await nextTick()

        const drawer = wrapper.find("[data-test='drawer']")
        const loadingContainer = drawer.find("[data-loading]")
        expect(loadingContainer.attributes("data-loading")).toBe("true")

        resolvePromise({done: true})
        await flushPromises()

        expect(loadingContainer.attributes("data-loading")).toBe("false")
    })

    it("the loaded outputs are rendered", async () => {
        hasOutputsMock.value = true
        const outputsData = {resultStatus: "SUCCESS", recordsCount: 150}
        mockLoadTaskRunOutputs.mockResolvedValueOnce(outputsData)

        const wrapper = mountOutputs()
        await wrapper.findComponent(KsDropdownItem).trigger("click")
        await flushPromises()

        const vars = wrapper.findComponent(stubs.Vars)
        expect(vars.exists()).toBe(true)
        expect(vars.props("data")).toEqual(outputsData)
        expect(vars.props("execution")).toEqual({id: "exec-123"})
    })
})
