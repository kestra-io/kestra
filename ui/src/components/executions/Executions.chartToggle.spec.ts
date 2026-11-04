import {defineComponent, h} from "vue"
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"

const user = vi.hoisted(() => ({
    isAllowed: vi.fn(() => true),
    hasAnyAction: vi.fn(() => true),
}))
vi.mock("override/stores/auth", () => ({useAuthStore: () => ({user})}))

import {i18nMount} from "../../../tests/unit/i18nMount"
import {routedPinia} from "../../../tests/unit/memoryRouter"
import Executions from "./Executions.vue"

const KsDataTable = defineComponent({
    name: "KsDataTable",
    setup(_, {slots}) {
        return () => h("div", [slots.navbar?.(), slots.top?.()])
    },
})

const KsFilter = defineComponent({
    name: "KsFilter",
    inheritAttrs: false,
    setup(_, {slots}) {
        return () => h("div", slots.extra?.())
    },
})

describe("Executions chart toggle", () => {
    let originalTitle: string

    beforeEach(() => {
        originalTitle = document.title
        localStorage.clear()
    })

    afterEach(() => {
        document.title = originalTitle
    })

    it("sizes the counts and timeline toggle like the other toolbar buttons", () => {
        // Given
        const wrapper = i18nMount(Executions, {
            props: {namespace: "qa", topbar: false},
            global: {
                plugins: [routedPinia()],
                stubs: {KsDataTable, KsFilter, Sections: true},
            },
        })

        // When
        const toggle = wrapper.findComponent({name: "KsSegmented"})

        // Then
        expect(toggle.exists()).toBe(true)
        expect(toggle.props("size")).toBe("default")
    })
})
