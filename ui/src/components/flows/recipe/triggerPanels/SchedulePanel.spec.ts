import {describe, expect, it} from "vitest"
import {KsInput, KsSegmented, KsSelect} from "@kestra-io/design-system"

import {i18nMount} from "../../../../../tests/unit/i18nMount"
import {DEFAULT_CRON} from "../../../../utils/recipeToYaml"
import SchedulePanel from "./SchedulePanel.vue"

const presets = [
    {frequency: "daily", cron: "0 9 * * *"},
    {frequency: "hourly", cron: "0 * * * *"},
    {frequency: "weekly", cron: "0 9 * * 1"},
]

describe("SchedulePanel", () => {
    it.each(presets)("recognizes the $frequency cron and displays its hint", ({frequency, cron}) => {
        const wrapper = i18nMount(SchedulePanel, {props: {cron}})

        expect(wrapper.getComponent(KsSegmented).props("modelValue")).toBe(frequency)
        expect(wrapper.get(".hint").text()).toBe(`recipe.schedule.${frequency}_hint`)
        expect(wrapper.emitted("update:cron")).toBeUndefined()
    })

    it("keeps an unrecognized cron as custom without a hint", () => {
        const cron = "*/15 8-18 * * 1-5"
        const wrapper = i18nMount(SchedulePanel, {props: {cron}})

        expect(wrapper.getComponent(KsSegmented).props("modelValue")).toBe("custom")
        expect(wrapper.getComponent(KsInput).props("modelValue")).toBe(cron)
        expect(wrapper.find(".hint").exists()).toBe(false)
        expect(wrapper.emitted("update:cron")).toBeUndefined()
    })

    it.each(presets)("writes the $frequency cron when that frequency is selected", async ({frequency, cron}) => {
        const wrapper = i18nMount(SchedulePanel, {props: {cron: "*/15 * * * *"}})

        wrapper.getComponent(KsSegmented).vm.$emit("update:modelValue", frequency)
        await wrapper.vm.$nextTick()

        expect(wrapper.emitted("update:cron")).toEqual([[cron]])
        expect(wrapper.getComponent(KsInput).props("modelValue")).toBe(cron)
        expect(wrapper.get(".hint").text()).toBe(`recipe.schedule.${frequency}_hint`)
    })

    it.each(["0 9 * * *", "*/15 * * * *"])("leaves %s untouched when custom is selected", async (cron) => {
        const wrapper = i18nMount(SchedulePanel, {props: {cron}})

        wrapper.getComponent(KsSegmented).vm.$emit("update:modelValue", "custom")
        await wrapper.vm.$nextTick()

        expect(wrapper.getComponent(KsInput).props("modelValue")).toBe(cron)
        expect(wrapper.emitted("update:cron")).toBeUndefined()
    })

    it("updates the frequency and hint when the cron is edited", async () => {
        const wrapper = i18nMount(SchedulePanel, {props: {cron: DEFAULT_CRON}})
        const input = wrapper.get("[data-test=\"recipe-cron-input\"]")

        await input.setValue("0 * * * *")

        expect(wrapper.emitted("update:cron")).toEqual([["0 * * * *"]])
        expect(wrapper.getComponent(KsSegmented).props("modelValue")).toBe("hourly")
        expect(wrapper.get(".hint").text()).toBe("recipe.schedule.hourly_hint")

        await input.setValue("*/15 * * * *")

        expect(wrapper.emitted("update:cron")?.at(-1)).toEqual(["*/15 * * * *"])
        expect(wrapper.getComponent(KsSegmented).props("modelValue")).toBe("custom")
        expect(wrapper.find(".hint").exists()).toBe(false)
    })

    it("binds the timezone select and uses the default cron as the placeholder", async () => {
        const wrapper = i18nMount(SchedulePanel, {
            props: {cron: "", timezone: "Europe/Paris"},
        })
        const select = wrapper.getComponent(KsSelect)

        expect(select.props()).toMatchObject({modelValue: "Europe/Paris"})
        expect(wrapper.get("[data-test=\"recipe-cron-input\"]").attributes("placeholder")).toBe(DEFAULT_CRON)

        select.vm.$emit("update:modelValue", "America/New_York")
        await wrapper.vm.$nextTick()

        expect(wrapper.emitted("update:timezone")).toEqual([["America/New_York"]])
    })
})
