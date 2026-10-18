import {afterAll, afterEach, beforeAll, beforeEach, describe, expect, test, vi} from "vitest"
import {mount} from "@vue/test-utils"
import BlueprintIconStack from "./BlueprintIconStack.vue"
import type {PluginIconMap} from "../../utils/pluginUtils"

let resizeCallback: ResizeObserverCallback | null = null
const disconnectSpy = vi.fn()
const observeSpy = vi.fn()

class MockResizeObserver {
    constructor(callback: ResizeObserverCallback) {
        resizeCallback = callback
    }
    observe = observeSpy
    unobserve = vi.fn()
    disconnect = disconnectSpy
}

const mockIcons: PluginIconMap = {}

function mountStack(clses: string[]) {
    return mount(BlueprintIconStack, {
        props: {
            clses,
            icons: mockIcons,
        },
        global: {
            stubs: {
                TaskIcon: {
                    props: ["cls"],
                    template: "<div class=\"mock-task-icon\" :data-cls=\"cls\" />",
                },
            },
        },
    })
}

function triggerResize(width: number) {
    if (resizeCallback) {
        resizeCallback(
            [{contentRect: {width}} as unknown as ResizeObserverEntry],
            {} as ResizeObserver,
        )
    }
}

describe("BlueprintIconStack", () => {
    beforeAll(() => {
        vi.stubGlobal("ResizeObserver", MockResizeObserver)
    })

    afterAll(() => {
        vi.unstubAllGlobals()
    })

    beforeEach(() => {
        resizeCallback = null
        disconnectSpy.mockClear()
        observeSpy.mockClear()
    })

    afterEach(() => {
        vi.restoreAllMocks()
    })

    test("classes are deduped by their parent, so two tasks in the same plugin yield one icon", () => {
        const wrapper = mountStack([
            "io.kestra.plugin.fs.Read",
            "io.kestra.plugin.fs.Write",
        ])

        const icons = wrapper.findAll(".mock-task-icon")
        expect(icons).toHaveLength(1)
        expect(icons[0].attributes("data-cls")).toBe("io.kestra.plugin.fs")
    })

    test("a class with no dot is used as its own parent", () => {
        const wrapper = mountStack([
            "CustomTask",
            "io.kestra.plugin.fs.Read",
        ])

        const icons = wrapper.findAll(".mock-task-icon")
        expect(icons).toHaveLength(2)
        expect(icons[0].attributes("data-cls")).toBe("CustomTask")
        expect(icons[1].attributes("data-cls")).toBe("io.kestra.plugin.fs")
    })

    test("with unlimited width every unique icon renders and no badge appears", () => {
        const wrapper = mountStack([
            "a.A",
            "b.B",
            "c.C",
            "d.D",
            "e.E",
        ])

        expect(wrapper.findAll(".mock-task-icon")).toHaveLength(5)
        expect(wrapper.find(".blueprint-icon-stack__more").exists()).toBe(false)
    })

    test("when the icons do not fit, one slot is reserved for the badge", async () => {
        const wrapper = mountStack([
            "a.A",
            "b.B",
            "c.C",
            "d.D",
            "e.E",
        ])

        // Fitting capacity for 88px: floor((88 + 8) / 32) = 3 slots.
        // 1 slot reserved for badge -> 2 visible icons.
        triggerResize(88)
        await wrapper.vm.$nextTick()

        expect(wrapper.findAll(".mock-task-icon")).toHaveLength(2)
        expect(wrapper.find(".blueprint-icon-stack__more").exists()).toBe(true)
    })

    test("the badge shows the number of hidden icons", async () => {
        const wrapper = mountStack([
            "a.A",
            "b.B",
            "c.C",
            "d.D",
            "e.E",
        ])

        triggerResize(88)
        await wrapper.vm.$nextTick()

        const badge = wrapper.find(".blueprint-icon-stack__more")
        expect(badge.exists()).toBe(true)
        expect(badge.text()).toBe("+3")
    })

    test("a width of zero renders no icons", async () => {
        const wrapper = mountStack([
            "a.A",
            "b.B",
            "c.C",
        ])

        triggerResize(0)
        await wrapper.vm.$nextTick()

        expect(wrapper.findAll(".mock-task-icon")).toHaveLength(0)
    })

    test("the ResizeObserver is disconnected on unmount", () => {
        const wrapper = mountStack(["a.A"])
        wrapper.unmount()

        expect(disconnectSpy).toHaveBeenCalled()
    })
})
