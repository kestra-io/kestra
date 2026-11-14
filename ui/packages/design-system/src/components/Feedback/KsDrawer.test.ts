import {describe, test, expect, vi} from "vitest"
import {mount, flushPromises} from "@vue/test-utils"
import {ElDrawer, ElMessageBox} from "element-plus"
import KestraDesignSystem from "../../index"
import KsDrawer from "./KsDrawer.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsDrawer", () => {
    test("dirty asks for confirmation before an accidental close", () => {
        const confirm = vi.spyOn(ElMessageBox, "confirm").mockReturnValue(new Promise(() => {}))
        const wrapper = mount(KsDrawer, {props: {modelValue: true, dirty: true}, global: globalConfig})
        const done = vi.fn()

        wrapper.findComponent(ElDrawer).props("beforeClose")(done)

        expect(confirm).toHaveBeenCalledTimes(1)
        expect(done).not.toHaveBeenCalled()
        confirm.mockRestore()
    })

    test("renders when visible", () => {
        const wrapper = mount(KsDrawer, {
            props: {modelValue: true},
            slots: {default: "<p>Drawer content</p>"},
            global: globalConfig,
        })
        expect(wrapper).toBeTruthy()
    })

    test("emits update:modelValue event", async () => {
        const wrapper = mount(KsDrawer, {
            props: {modelValue: true},
            global: globalConfig,
        })
        wrapper.vm.$emit("update:modelValue", false)
        expect(wrapper.emitted("update:modelValue")).toBeTruthy()
    })

    test("forwards beforeClose to the underlying drawer", () => {
        const beforeClose = vi.fn()
        const wrapper = mount(KsDrawer, {
            props: {modelValue: true, beforeClose},
            global: globalConfig,
        })
        const done = vi.fn()
        wrapper.findComponent(ElDrawer).props("beforeClose")(done)
        expect(beforeClose).toHaveBeenCalledWith(done)
    })

    test("reflects full-screen state in the toggle icon when resizable", async () => {
        const wrapper = mount(KsDrawer, {
            props: {modelValue: true, resizable: true, title: "Diff"},
            attachTo: document.body,
            global: globalConfig,
        })
        await flushPromises()

        const expandIcon = document.querySelector(".kel-drawer__header .arrow-expand-icon")
        expect(expandIcon).toBeTruthy()

        const toggle = expandIcon!.closest("button") as HTMLElement
        toggle.click()
        await flushPromises()

        expect(document.querySelector(".kel-drawer__header .arrow-collapse-icon")).toBeTruthy()
        wrapper.unmount()
    })

    test("restores the initial size when leaving full screen", async () => {
        const wrapper = mount(KsDrawer, {
            props: {modelValue: true, resizable: true, size: "40%", title: "Diff"},
            attachTo: document.body,
            global: globalConfig,
        })
        await flushPromises()
        const drawer = wrapper.findComponent(ElDrawer)
        expect(drawer.props("size")).toBe("40%")

        const toggle = () => (document.querySelector(".kel-drawer__header .full-screen")!.closest("button") as HTMLElement).click()
        toggle()
        await flushPromises()
        expect(drawer.props("size")).toBe(`${window.innerWidth}px`)

        toggle()
        await flushPromises()
        expect(drawer.props("size")).toBe("40%")
        wrapper.unmount()
    })

    test("restores the dragged width when leaving full screen", async () => {
        const wrapper = mount(KsDrawer, {
            props: {modelValue: true, resizable: true, title: "Diff"},
            attachTo: document.body,
            global: globalConfig,
        })
        await flushPromises()
        const drawer = wrapper.findComponent(ElDrawer)
        drawer.vm.$emit("resize-end", new MouseEvent("mouseup"), 500)
        await flushPromises()

        const toggle = () => (document.querySelector(".kel-drawer__header .full-screen")!.closest("button") as HTMLElement).click()
        toggle()
        await flushPromises()
        toggle()
        await flushPromises()
        expect(drawer.props("size")).toBe("500px")
        wrapper.unmount()
    })

    test("restores the width held before dragging into full screen", async () => {
        const wrapper = mount(KsDrawer, {
            props: {modelValue: true, resizable: true, title: "Diff"},
            attachTo: document.body,
            global: globalConfig,
        })
        await flushPromises()
        const drawer = wrapper.findComponent(ElDrawer)
        drawer.vm.$emit("resize-end", new MouseEvent("mouseup"), 500)
        drawer.vm.$emit("resize-end", new MouseEvent("mouseup"), window.innerWidth)
        await flushPromises()

        const collapse = document.querySelector(".kel-drawer__header .full-screen")!.closest("button") as HTMLElement
        collapse.click()
        await flushPromises()
        expect(drawer.props("size")).toBe("500px")
        wrapper.unmount()
    })

    test("leaves the width to the stylesheet when not resizable and no size is given", () => {
        const wrapper = mount(KsDrawer, {props: {modelValue: true}, global: globalConfig})

        expect(wrapper.findComponent(ElDrawer).props("size")).toBe("")
    })
})
