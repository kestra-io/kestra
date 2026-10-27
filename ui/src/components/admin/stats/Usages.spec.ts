import {describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {dayjs} from "@kestra-io/design-system"
import {useMiscStore} from "override/stores/misc"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import {routedPinia} from "../../../../tests/unit/memoryRouter"
import Usages from "./Usages.vue"

const day = {
    groupBy: "day",
    duration: {count: 3, sum: "PT2H5M"},
    taskRunsDuration: {count: 7, sum: "PT3H20M"},
}

const messages = {
    "last 24 hours": "last 24 hours",
    "last 7 days": "last 7 days",
    "last 30 days": "last 30 days",
    "since {date}": "since {date}",
}

const dayStartingAgo = (days: number, count: number) => ({
    groupBy: "day",
    startDate: dayjs().subtract(days, "day").startOf("day").toISOString(),
    duration: {count, sum: "PT1M"},
    taskRunsDuration: {count, sum: "PT1M"},
})

async function mountUsages(props: object) {
    const wrapper = i18nMount(Usages, {props, messages, global: {plugins: [routedPinia()]}})
    await flushPromises()
    return wrapper
}

async function subtitles(props: object) {
    const wrapper = await mountUsages(props)
    return wrapper.findAll(".subtitle").map((subtitle) => subtitle.text())
}

async function cardValues(fetchedUsages: object) {
    const wrapper = await mountUsages({fetchedUsages})
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

    it("shouldNameTheWindowInTheSubtitleOfEveryExecutionCard", async () => {
        const usagesSince = (days: number) => ({executions: {dailyExecutionsCount: [dayStartingAgo(days, 1)]}})

        expect(await subtitles({fetchedUsages: usagesSince(1)})).toEqual(Array(4).fill("last 24 hours"))
        expect(await subtitles({fetchedUsages: usagesSince(7), period: 7})).toEqual(Array(4).fill("last 7 days"))
        expect(await subtitles({fetchedUsages: usagesSince(30), period: 30})).toEqual(Array(4).fill("last 30 days"))
    })

    it("shouldGiveTheStartDateWhenTheDataDoesNotCoverTheWindow", async () => {
        const fetchedUsages = {executions: {dailyExecutionsCount: [dayStartingAgo(12, 0), dayStartingAgo(11, 2), dayStartingAgo(1, 5)]}}
        const since = dayjs().subtract(11, "day").startOf("day").toDate().toLocaleDateString("en-GB", {day: "numeric", month: "short"})

        expect(await subtitles({fetchedUsages, period: 30})).toEqual(Array(4).fill(`since ${since}`))
    })

    it("shouldKeepTheWindowNameWhenTheDataStartsWithTheWindow", async () => {
        const fetchedUsages = {executions: {dailyExecutionsCount: [dayStartingAgo(7, 3), dayStartingAgo(2, 1)]}}

        expect(await subtitles({fetchedUsages, period: 7})).toEqual(Array(4).fill("last 7 days"))
    })

    it("shouldKeepTheWindowNameWhenThereAreNoExecutionsAtAll", async () => {
        const fetchedUsages = {executions: {dailyExecutionsCount: [dayStartingAgo(1, 0), dayStartingAgo(0, 0)]}}

        expect(await subtitles({fetchedUsages, period: 30})).toEqual(Array(4).fill("last 30 days"))
    })

    it("shouldLoadTheUsagesAgainWhenThePeriodChanges", async () => {
        const wrapper = i18nMount(Usages, {global: {plugins: [routedPinia()]}})
        const loadAllUsages = vi.spyOn(useMiscStore(), "loadAllUsages").mockResolvedValue({})
        await flushPromises()
        expect(wrapper.find("[data-test=usage-period]").exists()).toBe(true)

        await wrapper.setProps({period: 30})
        await flushPromises()

        expect(loadAllUsages).toHaveBeenLastCalledWith(30)
    })
})
