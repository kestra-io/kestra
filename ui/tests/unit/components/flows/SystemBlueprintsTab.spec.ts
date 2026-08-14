import {describe, test, expect, vi, beforeEach, afterAll} from "vitest"
import {mount, RouterLinkStub} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {createPinia} from "pinia"
import {RECIPE_PRESET_KEY} from "../../../../src/utils/storageKeys"

const push = vi.fn()
const route = {params: {tenant: "acme"} as Record<string, string>, query: {}}

vi.mock("vue-router", () => ({
    useRouter: () => ({push}),
    useRoute: () => route,
    RouterLink: RouterLinkStub,
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {systemNamespace: "kestra.system"}}),
}))

const messages = {
    en: {
        "recipe.section_title": "System flows",
        "recipe.section_subtitle": "Build an alert flow for your platform.",
        "recipe.browse_blueprints": "Browse blueprints",
        "recipe.start_blank": "Skip the steps and open an empty flow",
    },
}

const globalConfig = {
    global: {
        plugins: [createI18n({legacy: false, locale: "en", messages}), createPinia()],
        stubs: {
            RouterLink: RouterLinkStub,
            KsText: {template: "<span><slot /></span>"},
            KsIcon: {template: "<i><slot /></i>"},
            FlowRecipe: {name: "FlowRecipe", template: "<div data-test='flow-recipe' />", props: ["namespace"], emits: ["submit"]},
        },
    },
}

import SystemBlueprintsTab from "../../../../src/components/flows/SystemBlueprintsTab.vue"

afterAll(() => {
    localStorage.clear()
    sessionStorage.clear()
})

describe("SystemBlueprintsTab", () => {
    beforeEach(() => {
        push.mockClear()
        sessionStorage.clear()
    })

    test("scopes the recipe builder to the configured system namespace", () => {
        // Given / When
        const wrapper = mount(SystemBlueprintsTab, globalConfig)

        // Then
        expect(wrapper.findComponent({name: "FlowRecipe"}).props("namespace")).toBe("kestra.system")
    })

    test("prefers an explicit namespace prop over the configured one", () => {
        // Given / When
        const wrapper = mount(SystemBlueprintsTab, {...globalConfig, props: {namespace: "kestra.other"}})

        // Then
        expect(wrapper.findComponent({name: "FlowRecipe"}).props("namespace")).toBe("kestra.other")
    })

    test("hands the generated flow to the editor through sessionStorage", async () => {
        // Given
        const wrapper = mount(SystemBlueprintsTab, globalConfig)
        const yaml = "id: system-flow-alert\nnamespace: kestra.system\n"

        // When
        wrapper.findComponent({name: "FlowRecipe"}).vm.$emit("submit", {id: "system-flow-alert", namespace: "kestra.system", yaml})

        // Then
        expect(sessionStorage.getItem(RECIPE_PRESET_KEY)).toBe(yaml)
        expect(push).toHaveBeenCalledWith({
            name: "flows/create",
            params: {tenant: "acme"},
            query: {recipePreset: "true"},
        })
    })

    test("links out to the community blueprint catalog", () => {
        // Given / When
        const wrapper = mount(SystemBlueprintsTab, globalConfig)

        // Then
        const link = wrapper.findAllComponents(RouterLinkStub)
            .find(l => l.attributes("data-test") === "system-blueprints-link")
        expect(link).toBeDefined()
        expect((link!.props("to") as {name: string}).name).toBe("blueprints")
    })

    test("offers an escape hatch that skips the wizard for a blank flow", () => {
        // Given / When — the guided steps must not be the only way out
        const wrapper = mount(SystemBlueprintsTab, globalConfig)

        // Then — `blank` is param-driven, so FlowCreate opens the editor
        // directly instead of bouncing the user back to the funnel
        const link = wrapper.findAllComponents(RouterLinkStub)
            .find(l => l.attributes("data-test") === "system-blank-flow-link")
        expect(link).toBeDefined()
        const to = link!.props("to") as {name: string; query: Record<string, string>}
        expect(to.name).toBe("flows/create")
        expect(to.query.blank).toBe("true")
        expect(to.query.namespace).toBe("kestra.system")
    })
})
