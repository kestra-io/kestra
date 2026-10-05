import {describe, expect, it, vi} from "vitest"
import {defineComponent, h, isReadonly, ref} from "vue"
import {mount} from "@vue/test-utils"

const mockedWidth = ref(1200)

vi.mock("@vueuse/core", () => ({
    useElementSize: vi.fn(() => ({
        width: mockedWidth,
        height: ref(0),
    })),
}))

import {
    EDITOR_HEADER_BREAKPOINTS,
    provideEditorHeaderWidth,
    useEditorHeaderWidth,
} from "../../../src/composables/useEditorHeaderWidth"

describe("useEditorHeaderWidth", () => {
    it("returns Number.POSITIVE_INFINITY when no provider is present", () => {
        let width: ReturnType<typeof useEditorHeaderWidth> | undefined

        const Consumer = defineComponent({
            setup() {
                width = useEditorHeaderWidth()
                return () => h("div")
            },
        })

        mount(Consumer)

        expect(width?.value).toBe(Number.POSITIVE_INFINITY)
    })

    it("provides the width to a descendant component", () => {
        let providedWidth: ReturnType<typeof provideEditorHeaderWidth> | undefined
        let consumedWidth: ReturnType<typeof useEditorHeaderWidth> | undefined

        const Consumer = defineComponent({
            setup() {
                consumedWidth = useEditorHeaderWidth()
                return () => h("div")
            },
        })

        const Provider = defineComponent({
            setup() {
                const element = ref<HTMLElement>()
                providedWidth = provideEditorHeaderWidth(element)

                return () => h(Consumer)
            },
        })

        mount(Provider)

        expect(providedWidth?.value).toBe(1200)
        expect(consumedWidth?.value).toBe(1200)
    })

    it("returns a readonly ref", () => {
        let width: ReturnType<typeof useEditorHeaderWidth> | undefined

        const Consumer = defineComponent({
            setup() {
                width = useEditorHeaderWidth()
                return () => h("div")
            },
        })

        const Provider = defineComponent({
            setup() {
                const element = ref<HTMLElement>()
                provideEditorHeaderWidth(element)

                return () => h(Consumer)
            },
        })

        mount(Provider)

        expect(width).toBeDefined()
        expect(isReadonly(width!)).toBe(true)
    })

    it("defines the expected editor header breakpoints", () => {
        expect(EDITOR_HEADER_BREAKPOINTS).toEqual({
            iconOnlyControls: 1000,
            tabsAsDropdown: 500,
        })
    })
})