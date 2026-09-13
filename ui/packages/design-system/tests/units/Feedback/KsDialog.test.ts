import {describe, test, expect, vi} from "vitest"
import {mount, flushPromises} from "@vue/test-utils"
import {ElMessageBox} from "element-plus"
import KestraDesignSystem from "../../../src/index"
import KsDialog from "../../../src/components/Feedback/KsDialog.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsDialog", () => {
    test("dirty asks for confirmation before an accidental close", () => {
        const confirm = vi.spyOn(ElMessageBox, "confirm").mockReturnValue(new Promise(() => {}))
        const wrapper = mount(KsDialog, {props: {modelValue: true, dirty: true}, global: globalConfig})
        const done = vi.fn()

        wrapper.findComponent({name: "ElDialog"}).props("beforeClose")(done)

        expect(confirm).toHaveBeenCalledTimes(1)
        expect(done).not.toHaveBeenCalled()
        confirm.mockRestore()
    })

    test("dirtyMessage replaces the default confirmation text", () => {
        const confirm = vi.spyOn(ElMessageBox, "confirm").mockReturnValue(new Promise(() => {}))
        const wrapper = mount(KsDialog, {props: {modelValue: true, dirty: true, dirtyMessage: "Lose this run?"}, global: globalConfig})

        wrapper.findComponent({name: "ElDialog"}).props("beforeClose")(vi.fn())

        expect(confirm).toHaveBeenCalledWith("Lose this run?", expect.anything(), expect.anything())
        confirm.mockRestore()
    })

    test("closes straight away when not dirty", () => {
        const wrapper = mount(KsDialog, {props: {modelValue: true}, global: globalConfig})
        const done = vi.fn()

        wrapper.findComponent({name: "ElDialog"}).props("beforeClose")(done)

        expect(done).toHaveBeenCalledTimes(1)
    })

    test("renders when visible", () => {
        const wrapper = mount(KsDialog, {
            props: {modelValue: true, title: "Test Dialog"},
            slots: {default: "<p>Dialog content</p>"},
            global: globalConfig,
        })
        expect(wrapper).toBeTruthy()
    })

    test("emits close event", async () => {
        const wrapper = mount(KsDialog, {
            props: {modelValue: true, title: "Test"},
            global: globalConfig,
        })
        wrapper.vm.$emit("close")
        expect(wrapper.emitted("close")).toBeTruthy()
    })

    test("uses 750px width when large", () => {
        const wrapper = mount(KsDialog, {
            props: {modelValue: true, large: true},
            global: globalConfig,
        })
        expect(wrapper.findComponent({name: "ElDialog"}).props("width")).toBe("min(750px, 90vw)")
    })

    test("explicit width overrides large", () => {
        const wrapper = mount(KsDialog, {
            props: {modelValue: true, large: true, width: "60%"},
            global: globalConfig,
        })
        expect(wrapper.findComponent({name: "ElDialog"}).props("width")).toBe("60%")
    })

    test("wraps the body in a scrollbar when scrollable", async () => {
        const wrapper = mount(KsDialog, {
            props: {modelValue: true, scrollable: true},
            slots: {default: "<p>Long content</p>"},
            attachTo: document.body,
            global: globalConfig,
        })
        await flushPromises()

        const scrollbar = wrapper.findComponent({name: "ElScrollbar"})
        expect(scrollbar.exists()).toBe(true)
        expect(scrollbar.props("maxHeight")).toBe("65vh")
        expect(document.querySelector(".kel-dialog__scrollable-body")?.textContent).toContain("Long content")
        wrapper.unmount()
    })

    test("renders the body without a scrollbar by default", async () => {
        const wrapper = mount(KsDialog, {
            props: {modelValue: true},
            slots: {default: "<p>Content</p>"},
            attachTo: document.body,
            global: globalConfig,
        })
        await flushPromises()

        expect(wrapper.findComponent({name: "ElScrollbar"}).exists()).toBe(false)
        expect(document.querySelector(".kel-dialog__body")?.textContent).toContain("Content")
        wrapper.unmount()
    })
})
