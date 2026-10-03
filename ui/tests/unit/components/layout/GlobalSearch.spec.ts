import {nextTick} from "vue"
import {describe, expect, test, beforeEach, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"
import {createRouter, createWebHistory} from "vue-router"
import KestraDesignSystem from "@kestra-io/design-system"

vi.mock("@kestra-io/kestra-sdk/flows", () => ({
    searchFlows: vi.fn(async () => ({results: []})),
}))
vi.mock("@kestra-io/kestra-sdk/namespaces", () => ({
    searchNamespaces: vi.fn(async () => ({results: []})),
}))
vi.mock("override/components/useLeftMenu", () => ({
    useLeftMenu: () => ({menu: {value: []}}),
}))

import GlobalSearch from "../../../../src/components/layout/GlobalSearch.vue"

const press = (key: string, extras: KeyboardEventInit = {}) => {
    window.dispatchEvent(new KeyboardEvent("keydown", {key, bubbles: true, cancelable: true, ...extras}))
}

describe("GlobalSearch", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    test("Ctrl+K focuses the search box and later keys type into it without a click", async () => {
        const router = createRouter({
            history: createWebHistory(),
            routes: [{path: "/", name: "home", component: {template: "<div />"}}],
        })
        const i18n = createI18n({legacy: false, locale: "en", messages: {en: {}}})
        const wrapper = mount(GlobalSearch, {
            attachTo: document.body,
            global: {plugins: [i18n, router, KestraDesignSystem]},
        })

        const editor = document.createElement("textarea")
        document.body.appendChild(editor)
        try {
            editor.focus()

            press("k", {ctrlKey: true})
            await nextTick()

            const input = document.querySelector<HTMLInputElement>(".search-modal input")
            expect(input).not.toBeNull()
            expect(document.activeElement).toBe(input)

            editor.focus()
            press("d")
            await nextTick()

            expect(input!.value).toBe("d")
            expect(document.activeElement).toBe(input)
        } finally {
            editor.remove()
            wrapper.unmount()
        }
    })
})
