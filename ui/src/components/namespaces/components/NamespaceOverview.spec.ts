import {describe, it, expect, vi, beforeEach} from "vitest"
import {defineComponent, h} from "vue"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../../tests/unit/i18nMount"

const current = vi.hoisted(() => ({route: {params: {id: "company.alpha"}} as {params: Record<string, string>}}))

vi.mock("vue-router", async (importOriginal) => {
    const {reactive} = await import("vue")
    current.route = reactive(current.route)
    return {...await importOriginal<typeof import("vue-router")>(), useRoute: () => current.route}
})

const findExecutions = vi.hoisted(() => vi.fn())
vi.mock("../../../stores/executions", () => ({
    useExecutionsStore: () => ({findExecutions}),
}))

vi.mock("../../dashboard/Dashboard.vue", () => ({
    default: defineComponent({name: "Dashboard", setup: () => () => h("div", {"data-test": "dashboard"})}),
}))
vi.mock("../../flows/NoExecutions.vue", () => ({
    default: defineComponent({name: "NoExecutions", setup: () => () => h("div", {"data-test": "no-executions"})}),
}))

import NamespaceOverview from "./NamespaceOverview.vue"

const totals: Record<string, number> = {"company.alpha": 3, "company.beta": 0}

describe("NamespaceOverview", () => {
    beforeEach(() => {
        current.route.params = {id: "company.alpha"}
        findExecutions.mockReset()
        findExecutions.mockImplementation((query: Record<string, string>) => Promise.resolve(totals[query["filters[namespace][PREFIX]"]]))
    })

    it("counts the executions of the namespace it switches to", async () => {
        current.route.params = {id: "company.beta"}
        const wrapper = i18nMount(NamespaceOverview)
        await flushPromises()
        expect(wrapper.find("[data-test=no-executions]").exists()).toBe(true)

        current.route.params = {id: "company.alpha"}
        await flushPromises()

        expect(wrapper.find("[data-test=dashboard]").exists()).toBe(true)
        expect(wrapper.find("[data-test=no-executions]").exists()).toBe(false)
    })

    it("falls back to the empty state when the count of the new namespace fails", async () => {
        const wrapper = i18nMount(NamespaceOverview)
        await flushPromises()
        expect(wrapper.find("[data-test=dashboard]").exists()).toBe(true)

        findExecutions.mockRejectedValueOnce(new Error("count failed"))
        current.route.params = {id: "company.beta"}
        await flushPromises()

        expect(wrapper.find("[data-test=no-executions]").exists()).toBe(true)
        expect(wrapper.find("[data-test=dashboard]").exists()).toBe(false)
    })

    it("ignores the count of a namespace already left", async () => {
        let answerAlpha: (total: number) => void = () => {}
        findExecutions.mockImplementationOnce(() => new Promise<number>((resolve) => answerAlpha = resolve))
        const wrapper = i18nMount(NamespaceOverview)

        current.route.params = {id: "company.beta"}
        await flushPromises()
        answerAlpha(3)
        await flushPromises()

        expect(wrapper.find("[data-test=no-executions]").exists()).toBe(true)
        expect(wrapper.find("[data-test=dashboard]").exists()).toBe(false)
    })
})
