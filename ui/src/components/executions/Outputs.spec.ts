import {beforeEach, describe, expect, it, vi} from "vitest"
import {ref, nextTick} from "vue"
import Outputs from "./Outputs.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"

const hasOutputsRef = ref(true)
const mockLoadTaskRunOutputs = vi.fn()

vi.mock("../../composables/useTaskRunOutputs", () => ({
    useHasTaskRunOutputs: () => hasOutputsRef,
    loadTaskRunOutputs: (...args: unknown[]) => mockLoadTaskRunOutputs(...args),
}))

vi.mock("@kestra-io/design-system", async (importOriginal) => {
    const actual = await importOriginal<Record<string, unknown>>()
    return {
        ...actual,
        vKsLoading: {
            mounted(el: HTMLElement, binding: {value: boolean}) {
                el.setAttribute("data-loading", String(binding.value))
            },
            updated(el: HTMLElement, binding: {value: boolean}) {
                el.setAttribute("data-loading", String(binding.value))
            },
        },
    }
})

vi.mock("vue-material-design-icons/LocationExit.vue", () => ({
    default: {name: "LocationExit", template: "<span />"},
}))

const stubs = {
    KsDropdownItem: {
        props: ["disabled"],
        emits: ["click"],
        template: "<button data-test=\"outputs-trigger\" :disabled=\"disabled\" @click=\"$emit('click')\"><slot /></button>",
    },
    KsDrawer: {
        props: ["modelValue", "title"],
        emits: ["update:modelValue"],
        template: "<div v-if=\"modelValue\" data-test=\"outputs-drawer\"><slot /></div>",
    },
    Vars: {
        props: ["execution", "data"],
        template: "<div data-test=\"vars\" :data-outputs=\"JSON.stringify(data)\" />",
    },
}

function mountOutputs(propsOverrides = {}) {
    return i18nMount(Outputs, {
        props: {
            taskRun: {id: "task-run-1", state: {current: "SUCCESS"}},
            executionId: "exec-1",
            execution: {id: "exec-1"},
            ...propsOverrides,
        },
        global: {
            stubs,
        },
    })
}

describe("Outputs.vue", () => {
    beforeEach(() => {
        vi.clearAllMocks()
        hasOutputsRef.value = true
        mockLoadTaskRunOutputs.mockResolvedValue({result: "success"})
    })

    it("should be disabled when the task run has no outputs", () => {
        hasOutputsRef.value = false
        const wrapper = mountOutputs()

        const trigger = wrapper.find("[data-test=outputs-trigger]")
        expect(trigger.attributes("disabled")).toBeDefined()
    })

    it("should be enabled when outputs exist", () => {
        hasOutputsRef.value = true
        const wrapper = mountOutputs()

        const trigger = wrapper.find("[data-test=outputs-trigger]")
        expect(trigger.attributes("disabled")).toBeUndefined()
    })

    it("should lazily load outputs when the panel is opened", async () => {
        const wrapper = mountOutputs()

        expect(mockLoadTaskRunOutputs).not.toHaveBeenCalled()
        expect(wrapper.find("[data-test=outputs-drawer]").exists()).toBe(false)

        await wrapper.find("[data-test=outputs-trigger]").trigger("click")
        await nextTick()

        expect(mockLoadTaskRunOutputs).toHaveBeenCalledTimes(1)
        expect(mockLoadTaskRunOutputs).toHaveBeenCalledWith("exec-1", "task-run-1")
        expect(wrapper.find("[data-test=outputs-drawer]").exists()).toBe(true)
    })

    it("should not refetch outputs when reopened after outputs are already loaded", async () => {
        const wrapper = mountOutputs()

        await wrapper.find("[data-test=outputs-trigger]").trigger("click")
        await nextTick()
        expect(mockLoadTaskRunOutputs).toHaveBeenCalledTimes(1)

        await wrapper.find("[data-test=outputs-trigger]").trigger("click")
        await nextTick()
        expect(wrapper.find("[data-test=outputs-drawer]").exists()).toBe(false)

        await wrapper.find("[data-test=outputs-trigger]").trigger("click")
        await nextTick()
        expect(wrapper.find("[data-test=outputs-drawer]").exists()).toBe(true)

        expect(mockLoadTaskRunOutputs).toHaveBeenCalledTimes(1)
    })

    it("should display loading state while fetch is in flight", async () => {
        let resolveOutputs!: (val: Record<string, unknown>) => void
        mockLoadTaskRunOutputs.mockReturnValue(
            new Promise((resolve) => {
                resolveOutputs = resolve
            }),
        )

        const wrapper = mountOutputs()

        await wrapper.find("[data-test=outputs-trigger]").trigger("click")
        await nextTick()

        const loadingContainer = wrapper.find("[data-loading]")
        expect(loadingContainer.exists()).toBe(true)
        expect(loadingContainer.attributes("data-loading")).toBe("true")

        resolveOutputs({key: "value"})
        await nextTick()
        await nextTick()

        expect(loadingContainer.attributes("data-loading")).toBe("false")
    })

    it("should render loaded outputs in Vars component", async () => {
        mockLoadTaskRunOutputs.mockResolvedValue({status: "completed", count: 42})
        const wrapper = mountOutputs()

        await wrapper.find("[data-test=outputs-trigger]").trigger("click")
        await nextTick()
        await nextTick()

        const vars = wrapper.find("[data-test=vars]")
        expect(vars.exists()).toBe(true)
        expect(JSON.parse(vars.attributes("data-outputs") || "{}")).toEqual({
            status: "completed",
            count: 42,
        })
    })
})
