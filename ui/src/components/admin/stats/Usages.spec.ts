import {describe, expect, it} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {createPinia} from "pinia"
import {createMemoryHistory, createRouter} from "vue-router"
import KestraDesignSystem from "@kestra-io/design-system"
import Usages from "./Usages.vue"

const day = {
    groupBy: "day",
    duration: {count: 3, sum: "PT2H5M"},
    taskRunsDuration: {count: 7, sum: "PT3H20M"},
}

async function cardValues(fetchedUsages: object) {
    const i18n = createI18n({legacy: false, locale: "en", missingWarn: false, fallbackWarn: false, messages: {en: {}}})
    const router = createRouter({history: createMemoryHistory(), routes: [{path: "/:pathMatch(.*)*", component: {render: () => null}}]})
    const wrapper = mount(Usages, {props: {fetchedUsages}, global: {plugins: [i18n, KestraDesignSystem, createPinia(), router]}})
    await flushPromises()
    return Object.fromEntries(
        wrapper.findAll("[data-test=usage-card]").map((card) => [
            card.find("[data-test=usage-card-label]").text(),
            card.find("[data-test=usage-card-value]").text(),
        ]),
    )
}

describe("Usages", () => {
    it("shouldSumDurationsInMinutesWhenTotalExceedsOneHour", async () => {
        const values = await cardValues({executions: {dailyExecutionsCount: [day, day]}})

        expect(values["executions duration (in minutes)"]).toBe("250")
        expect(values["task runs duration (in minutes)"]).toBe("400")
        expect(values["task runs"]).toBe("14")
    })

    it("shouldIgnoreDaysWhenTaskRunsDurationIsMissing", async () => {
        const values = await cardValues({
            executions: {dailyExecutionsCount: [{groupBy: "day", duration: {count: 1, sum: "PT10M"}}]},
        })

        expect(values["task runs"]).toBe("0")
        expect(values["task runs duration (in minutes)"]).toBe("0")
    })
})
