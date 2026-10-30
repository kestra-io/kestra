import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h} from "vue"
import {mount, VueWrapper} from "@vue/test-utils"
import type {useMiscStore} from "override/stores/misc"
import type {SelectedTheme} from "../utils/utils"

const switchThemeMock = vi.hoisted(() => vi.fn())

vi.mock("../utils/utils", () => ({
    switchTheme: switchThemeMock,
}))

import {useThemeCycle} from "./useThemeCycle"

type MiscStoreStub = { theme: SelectedTheme | undefined }

let stub: MiscStoreStub

function mountThemeCycle() {
    const Host = defineComponent({
        name: "ThemeCycleHost",
        setup() {
            useThemeCycle(stub as unknown as ReturnType<typeof useMiscStore>)
        },
        render: () => h("div"),
    })

    return mount(Host)
}

function ctrlShiftL(key = "L") {
    window.dispatchEvent(
        new KeyboardEvent("keydown", {key, ctrlKey: true, shiftKey: true}),
    )
}

describe("useThemeCycle", () => {
    let wrapper: VueWrapper

    beforeEach(() => {
        switchThemeMock.mockReset()
        switchThemeMock.mockImplementation(
            (store: MiscStoreStub, theme: SelectedTheme) => {
                store.theme = theme
            },
        )
        stub = {theme: "light"}
    })

    afterEach(() => {
        wrapper?.unmount()
    })

    // -- cycling ----------------------------------------------------
    it("cycles from light to dark-2", () => {
        wrapper = mountThemeCycle()
        ctrlShiftL()
        expect(switchThemeMock).toHaveBeenCalledWith(stub, "dark-2")
    })

    it("cycles from dark-2 to dark", () => {
        stub.theme = "dark-2"
        wrapper = mountThemeCycle()
        ctrlShiftL()
        expect(switchThemeMock).toHaveBeenCalledWith(stub, "dark")
    })

    it("cycles from dark to light", () => {
        stub.theme = "dark"
        wrapper = mountThemeCycle()
        ctrlShiftL()
        expect(switchThemeMock).toHaveBeenCalledWith(stub, "light")
    })

    it("completes a full cycle: light -> dark-2 -> dark -> light", () => {
        stub.theme = "light"
        wrapper = mountThemeCycle()

        ctrlShiftL()
        expect(stub.theme).toBe("dark-2")

        ctrlShiftL()
        expect(stub.theme).toBe("dark")

        ctrlShiftL()
        expect(stub.theme).toBe("light")

        expect(switchThemeMock).toHaveBeenCalledTimes(3)
    })

    // -- case-insensitive key ---------------------------------------
    it("triggers on uppercase L", () => {
        wrapper = mountThemeCycle()
        ctrlShiftL("L")
        expect(switchThemeMock).toHaveBeenCalledOnce()
    })

    it("triggers on lowercase l", () => {
        wrapper = mountThemeCycle()
        ctrlShiftL("l")
        expect(switchThemeMock).toHaveBeenCalledOnce()
    })

    // -- ignored modifiers / wrong key ------------------------------
    it.each([
        {desc: "without ctrlKey", opts: {key: "L", shiftKey: true}},
        {desc: "without shiftKey", opts: {key: "L", ctrlKey: true}},
        {desc: "with a different key", opts: {key: "k", ctrlKey: true, shiftKey: true}},
    ])("does nothing $desc", ({opts}) => {
        wrapper = mountThemeCycle()
        window.dispatchEvent(new KeyboardEvent("keydown", opts))
        expect(switchThemeMock).not.toHaveBeenCalled()
    })

    // -- unset theme defaults to light ------------------------------
    it("treats undefined theme as light and cycles to dark-2", () => {
        stub.theme = undefined
        wrapper = mountThemeCycle()
        ctrlShiftL()
        expect(switchThemeMock).toHaveBeenCalledWith(stub, "dark-2")
    })

    // -- listener cleanup on unmount --------------------------------
    it("removes the keydown listener on unmount", () => {
        const addSpy = vi.spyOn(window, "addEventListener")
        const removeSpy = vi.spyOn(window, "removeEventListener")

        wrapper = mountThemeCycle()

        // Control: listener fires while mounted
        ctrlShiftL()
        expect(switchThemeMock).toHaveBeenCalledOnce()

        // Capture the handler that was registered
        const addCall = addSpy.mock.calls.find(([event]) => event === "keydown")
        expect(addCall).toBeDefined()
        const handler = addCall![1]

        wrapper.unmount()

        // removeEventListener was called with the same handler
        const removeCall = removeSpy.mock.calls.find(([event]) => event === "keydown")
        expect(removeCall).toBeDefined()
        expect(removeCall![1]).toBe(handler)

        // After unmount the listener no longer fires
        switchThemeMock.mockClear()
        ctrlShiftL()
        expect(switchThemeMock).not.toHaveBeenCalled()

        addSpy.mockRestore()
        removeSpy.mockRestore()
    })
})
