import {afterEach, beforeEach, describe, expect, test} from "vitest"
import {defineComponent} from "vue"
import {flushPromises, mount, type VueWrapper} from "@vue/test-utils"
import {useTheme} from "../../../src/composables/useTheme"

let wrapper: VueWrapper | undefined
let originalClassName: string

function mountTheme() {
    let theme!: ReturnType<typeof useTheme>

    wrapper = mount(defineComponent({
        setup() {
            theme = useTheme()
            return () => null
        },
    }))

    return theme
}

beforeEach(() => {
    originalClassName = document.documentElement.className
    document.documentElement.className = ""
})

afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    document.documentElement.className = originalClassName
})

describe("useTheme", () => {
    test("detects the dark class already present at mount", () => {
        document.documentElement.classList.add("dark")

        const {isDark} = mountTheme()

        expect(isDark.value).toBe(true)
    })

    test("is not dark when the dark class is absent", () => {
        const {isDark} = mountTheme()

        expect(isDark.value).toBe(false)
    })

    test("reacts when the dark class is added", async () => {
        const {isDark} = mountTheme()

        document.documentElement.classList.add("dark")
        await flushPromises()

        expect(isDark.value).toBe(true)
    })

    test("reacts when the dark class is removed", async () => {
        document.documentElement.classList.add("dark")
        const {isDark} = mountTheme()

        document.documentElement.classList.remove("dark")
        await flushPromises()

        expect(isDark.value).toBe(false)
    })

    test("does not change for an unrelated class mutation", async () => {
        document.documentElement.classList.add("dark")
        const {isDark} = mountTheme()

        document.documentElement.classList.add("unrelated")
        await flushPromises()

        expect(isDark.value).toBe(true)
    })

    test("stops reacting to class changes after unmount", async () => {
        document.documentElement.classList.add("dark")
        const {isDark} = mountTheme()

        expect(isDark.value).toBe(true)

        wrapper!.unmount()
        wrapper = undefined

        document.documentElement.classList.remove("dark")
        await flushPromises()

        expect(isDark.value).toBe(true)
    })
})
