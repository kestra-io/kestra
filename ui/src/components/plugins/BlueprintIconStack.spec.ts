import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {nextTick} from "vue"
import {i18nMount} from "../../../tests/unit/i18nMount"
import BlueprintIconStack from "./BlueprintIconStack.vue"

const clses = [
    "io.kestra.plugin.core.log.Log",
    "io.kestra.plugin.fs.http.Request",
    "io.kestra.plugin.git.Clone",
    "io.kestra.plugin.jdbc.Query",
]
const parents = [
    "io.kestra.plugin.core.log",
    "io.kestra.plugin.fs.http",
    "io.kestra.plugin.git",
    "io.kestra.plugin.jdbc",
]

let onResize: ResizeObserverCallback
const observer: ResizeObserver = {
    observe: vi.fn(),
    unobserve: vi.fn(),
    disconnect: vi.fn(),
}

function mountStack(classes = clses) {
    const wrapper = i18nMount(BlueprintIconStack, {props: {clses: classes, icons: {}}})

    return {
        wrapper,
        iconLabels: () => wrapper.findAll("img").map(icon => icon.attributes("alt")),
        async resize(width: number) {
            onResize([{
                target: wrapper.element,
                contentRect: new DOMRectReadOnly(0, 0, width, 24),
                borderBoxSize: [],
                contentBoxSize: [],
                devicePixelContentBoxSize: [],
            }], observer)
            await nextTick()
        },
    }
}

beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal("ResizeObserver", vi.fn(function (callback: ResizeObserverCallback) {
        onResize = callback
        return observer
    }))
})

afterEach(() => vi.unstubAllGlobals())

describe("BlueprintIconStack", () => {
    it("deduplicates tasks by parent while preserving the first occurrence order", () => {
        const {iconLabels} = mountStack([
            clses[0],
            clses[1],
            "io.kestra.plugin.core.log.OtherLog",
            clses[2],
        ])

        expect(iconLabels()).toEqual(parents.slice(0, 3))
    })

    it("uses a class without a dot as its own parent", () => {
        const {iconLabels} = mountStack(["CustomTask", "CustomTask", clses[0]])

        expect(iconLabels()).toEqual(["CustomTask", parents[0]])
    })

    it("renders every unique icon without an overflow badge before width is constrained", () => {
        const {wrapper, iconLabels} = mountStack()

        expect(iconLabels()).toEqual(parents)
        expect(wrapper.text()).toBe("")
    })

    it("reserves a slot for the badge and counts all hidden icons", async () => {
        const {wrapper, iconLabels, resize} = mountStack()

        await resize(88)

        expect(iconLabels()).toEqual(parents.slice(0, 2))
        expect(wrapper.text()).toBe("+2")
    })

    it("restores all icons and removes the badge when the container grows to fit them", async () => {
        const {wrapper, iconLabels, resize} = mountStack()

        await resize(88)
        await resize(120)

        expect(iconLabels()).toEqual(parents)
        expect(wrapper.text()).toBe("")
    })

    it("renders no icons when the observed width is zero", async () => {
        const {iconLabels, resize} = mountStack()

        await resize(0)

        expect(iconLabels()).toEqual([])
    })

    it("observes the container and disconnects the observer on unmount", () => {
        const {wrapper} = mountStack()

        expect(observer.observe).toHaveBeenCalledWith(wrapper.element)
        expect(observer.disconnect).not.toHaveBeenCalled()

        wrapper.unmount()

        expect(observer.disconnect).toHaveBeenCalledOnce()
    })
})
