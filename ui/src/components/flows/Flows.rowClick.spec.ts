import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {reactive} from "vue"
import {createI18n} from "vue-i18n"
import {shallowMount} from "@vue/test-utils"
import KestraDesignSystem from "@kestra-io/design-system"

const {push} = vi.hoisted(() => ({push: vi.fn()}))

const route = reactive({
    name: "flows/list",
    params: {tenant: "main"} as Record<string, string>,
    query: {} as Record<string, string>,
    meta: {},
})

vi.mock("vue-router", async (importOriginal) => ({
    ...(await importOriginal<typeof import("vue-router")>()),
    useRoute: () => route,
    useRouter: () => ({push, replace: vi.fn(), resolve: vi.fn(() => ({href: ""}))}),
}))

import Flows from "./Flows.vue"

const row = {id: "test-alert", namespace: "kestra.data.dbt"}

function clickRow() {
    const i18n = createI18n({legacy: false, locale: "en", messages: {en: {}}, missingWarn: false, fallbackWarn: false})
    const wrapper = shallowMount(Flows, {global: {plugins: [createPinia(), i18n, KestraDesignSystem]}})
    const cell = document.createElement("td")
    wrapper.findComponent({name: "KsDataTable"}).vm.$emit("row-click", row, {}, {target: cell, button: 0} as unknown as MouseEvent)
}

describe("Flows row click", () => {
    let originalTitle: string

    beforeEach(() => {
        originalTitle = document.title
        setActivePinia(createPinia())
        push.mockClear()
    })

    afterEach(() => {
        document.title = originalTitle
    })

    it("opens the flow from the top-level Flows page", () => {
        Object.assign(route, {name: "flows/list", params: {tenant: "main"}})

        clickRow()

        expect(push).toHaveBeenCalledWith({name: "flows/update", params: {tenant: "main", namespace: "kestra.data.dbt", id: "test-alert"}})
    })

    it("opens the flow from a namespace's Flows tab", () => {
        Object.assign(route, {name: "namespaces/update/flows", params: {tenant: "main", id: "kestra.data.dbt"}})

        clickRow()

        expect(push).toHaveBeenCalledWith({name: "flows/update", params: {tenant: "main", namespace: "kestra.data.dbt", id: "test-alert"}})
    })
})
