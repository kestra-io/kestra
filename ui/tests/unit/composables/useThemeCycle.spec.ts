import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h} from "vue"
import {mount, type VueWrapper} from "@vue/test-utils"

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({
        get: vi.fn(),
        post: vi.fn(),
    }),
}))

const switchThemeMock = vi.fn()

vi.mock("../../../src/utils/utils", async () => {
    const actual = await vi.importActual<object>("../../../src/utils/utils")
    return {
        ...actual,
        switchTheme: (...args: unknown[]) => switchThemeMock(...args),
    }
})

import {useThemeCycle} from "../../../src/composables/useThemeCycle"
import type {SelectedTheme} from "../../../src/utils/utils"

function createStubMiscStore(initialTheme?: SelectedTheme) {
    return {
        theme: initialTheme,
    }
}

function mountHost(miscStore: ReturnType<typeof createStubMiscStore>) {
    return mount(defineComponent({
        setup() {
            useThemeCycle(miscStore as never)
            return () => h("div")
        },
    }))
}

function triggerShortcut(options: Partial<KeyboardEventInit> = {}) {
    window.dispatchEvent(new KeyboardEvent("keydown", {
        key: "l",
        ctrlKey: true,
        shiftKey: true,
        ...options,
    }))
}

describe("useThemeCycle", () => {
    let wrapper: VueWrapper | undefined

    beforeEach(() => {
        switchThemeMock.mockClear()
    })

    afterEach(() => {
        wrapper?.unmount()
        wrapper = undefined
    })

    it("advances light to dark-2, dark-2 to dark, and dark back to light", () => {
        const store = createStubMiscStore("light")
        wrapper = mountHost(store)

        triggerShortcut()
        expect(switchThemeMock).toHaveBeenCalledWith(store, "dark-2")

        store.theme = "dark-2"
        triggerShortcut()
        expect(switchThemeMock).toHaveBeenCalledWith(store, "dark")

        store.theme = "dark"
        triggerShortcut()
        expect(switchThemeMock).toHaveBeenCalledWith(store, "light")
    })

    it("handles key check case-insensitively for L and l", () => {
        const store = createStubMiscStore("light")
        wrapper = mountHost(store)

        triggerShortcut({key: "L"})
        expect(switchThemeMock).toHaveBeenCalledWith(store, "dark-2")

        switchThemeMock.mockClear()
        triggerShortcut({key: "l"})
        expect(switchThemeMock).toHaveBeenCalledWith(store, "dark-2")
    })

    it("does nothing without Ctrl, without Shift, or with a different key", () => {
        const store = createStubMiscStore("light")
        wrapper = mountHost(store)

        triggerShortcut({ctrlKey: false})
        triggerShortcut({shiftKey: false})
        triggerShortcut({key: "k"})

        expect(switchThemeMock).not.toHaveBeenCalled()
    })

    it("treats an unset theme as light", () => {
        const store = createStubMiscStore(undefined)
        wrapper = mountHost(store)

        triggerShortcut()
        expect(switchThemeMock).toHaveBeenCalledWith(store, "dark-2")
    })

    it("removes the keydown listener on unmount", () => {
        const store = createStubMiscStore("light")
        wrapper = mountHost(store)

        wrapper.unmount()
        wrapper = undefined

        triggerShortcut()
        expect(switchThemeMock).not.toHaveBeenCalled()
    })
})
