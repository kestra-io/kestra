import {describe, expect, it} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import {routedPinia} from "../../../../tests/unit/memoryRouter"
import Usages from "./Usages.vue"

const day = {
    groupBy: "day",
    duration: {count: 3, sum: "PT2H5M"},
    taskRunsDuration: {count: 7, sum: "PT3H20M"},
}

async function cardValues(fetchedUsages: object) {
    const wrapper = i18nMount(Usages, {props: {fetchedUsages}, global: {plugins: [routedPinia()]}})
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
