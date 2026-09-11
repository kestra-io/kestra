import {describe, test, expect, vi, beforeAll} from "vitest"
import {mount, type DOMWrapper, type VueWrapper} from "@vue/test-utils"
import {nextTick} from "vue"
import KestraDesignSystem from "../../../src/index"
import KsRangeSlider from "../../../src/components/Form/KsRangeSlider.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

beforeAll(() => {
    Element.prototype.setPointerCapture = vi.fn()
})

function stubTrackWidth(wrapper: VueWrapper, width: number) {
    const track = wrapper.get("[data-test='range-slider-track']").element as HTMLElement
    vi.spyOn(track, "getBoundingClientRect").mockReturnValue({
        left: 0, right: width, width, top: 0, bottom: 0, height: 0, x: 0, y: 0, toJSON: () => undefined,
    } as DOMRect)
}

function mountSlider(modelValue: [number, number] = [20, 80], disabled = false) {
    const wrapper = mount(KsRangeSlider, {
        props: {min: 0, max: 100, modelValue, startLabel: "Start", endLabel: "End", disabled},
        global: globalConfig,
    })
    stubTrackWidth(wrapper, 200)
    return wrapper
}

// vue-test-utils' `trigger()` reassigns event-init properties after construction, but jsdom defines
// MouseEvent/PointerEvent coordinate properties as own getter-only accessors, so a post-hoc
// `event.clientX = ...` throws. Dispatch a real PointerEvent instead, whose init dict sets them
// at construction time.
async function firePointer(target: DOMWrapper<Element>, type: string, init: PointerEventInit) {
    target.element.dispatchEvent(new PointerEvent(type, {bubbles: true, ...init}))
    await nextTick()
}

describe("KsRangeSlider", () => {
    test("renders both handles reflecting the model value in aria-valuenow", () => {
        const wrapper = mountSlider([20, 80])
        expect(wrapper.get("[data-test='range-slider-handle-start']").attributes("aria-valuenow")).toBe("20")
        expect(wrapper.get("[data-test='range-slider-handle-end']").attributes("aria-valuenow")).toBe("80")
    })

    test("dragging the start handle resizes the window and emits change on release", async () => {
        const wrapper = mountSlider([20, 80])
        const handle = wrapper.get("[data-test='range-slider-handle-start']")

        await firePointer(handle, "pointerdown", {clientX: 40, pointerId: 1})
        await firePointer(handle, "pointermove", {clientX: 50, pointerId: 1})
        await firePointer(handle, "pointerup", {clientX: 50, pointerId: 1})

        expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toEqual([25, 80])
        expect(wrapper.emitted("change")?.at(-1)?.[0]).toEqual([25, 80])
    })

    test("dragging the selection body pans the whole window without resizing it", async () => {
        const wrapper = mountSlider([20, 80])
        const body = wrapper.get("[data-test='range-slider-selection']")

        await firePointer(body, "pointerdown", {clientX: 100, pointerId: 2})
        await firePointer(body, "pointermove", {clientX: 140, pointerId: 2})
        await firePointer(body, "pointerup", {clientX: 140, pointerId: 2})

        expect(wrapper.emitted("change")?.at(-1)?.[0]).toEqual([40, 100])
    })

    test("clamps the end handle to the domain max", async () => {
        const wrapper = mountSlider([20, 80])
        const handle = wrapper.get("[data-test='range-slider-handle-end']")

        await firePointer(handle, "pointerdown", {clientX: 160, pointerId: 3})
        await firePointer(handle, "pointermove", {clientX: 400, pointerId: 3})
        await firePointer(handle, "pointerup", {clientX: 400, pointerId: 3})

        expect(wrapper.emitted("change")?.at(-1)?.[0]).toEqual([20, 100])
    })

    test("ArrowRight nudges the focused start handle by one step and emits change", async () => {
        const wrapper = mountSlider([20, 80])
        const handle = wrapper.get("[data-test='range-slider-handle-start']")

        await handle.trigger("keydown", {key: "ArrowRight"})

        expect(wrapper.emitted("change")?.at(-1)?.[0]).toEqual([21, 80])
    })

    test("disabled prevents dragging from changing the value", async () => {
        const wrapper = mountSlider([20, 80], true)
        const handle = wrapper.get("[data-test='range-slider-handle-start']")

        await firePointer(handle, "pointerdown", {clientX: 40, pointerId: 4})
        await firePointer(handle, "pointermove", {clientX: 90, pointerId: 4})
        await firePointer(handle, "pointerup", {clientX: 90, pointerId: 4})

        expect(wrapper.emitted("change")).toBeUndefined()
    })
})
