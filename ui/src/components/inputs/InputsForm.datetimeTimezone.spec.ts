import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../tests/unit/i18nMount"

import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"
import InputsForm from "./InputsForm.vue"
import en from "../../translations/en.json"
import {useExecutionsStore, type InputMetaData} from "../../stores/executions"
import type {Flow} from "../../stores/flow"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}, name: "flow"}),
    useRouter: () => ({replace: vi.fn(), push: vi.fn()}),
}))

const flow = {namespace: "company.team", id: "datetime_tz"} as Flow

// https://github.com/kestra-io/kestra-ee/issues/12807
describe("InputsForm DATETIME input time zone", () => {
    const originalTz = process.env.TZ

    beforeEach(() => {
        setActivePinia(createPinia())
        process.env.TZ = "Asia/Kolkata"
        localStorage.setItem("timezone", "Europe/Paris")
    })

    afterEach(() => {
        // Assigning undefined would store the string "undefined", so drop the variable when it was unset.
        if (originalTz === undefined) delete process.env.TZ
        else process.env.TZ = originalTz
        localStorage.removeItem("timezone")
        document.body.innerHTML = ""
    })

    async function mountDatetime(value?: string) {
        const input: InputMetaData = {id: "change_at", type: "DATETIME", required: false}
        useExecutionsStore().validateExecution = vi.fn(() => Promise.resolve({
            checks: [],
            inputs: [{enabled: true, isDefault: false, value, input}],
        }))

        const wrapper = i18nMount(InputsForm, {
            global: {plugins: [KestraDesignSystem]},
            props: {flow, initialInputs: [input]},
            locales: en,
        })
        await flushPromises()
        return wrapper
    }

    test("stores the typed value as a wall clock in the Time Zone preference, not the browser zone", async () => {
        const wrapper = await mountDatetime()
        const picker = wrapper.findComponent({name: "ElDatePicker"})

        // Element Plus emits a browser-local Date: 09:00 as typed by the user.
        picker.vm.$emit("update:modelValue", new Date(2026, 10, 24, 9, 0, 0))
        await flushPromises()

        const emitted = wrapper.emitted("update:modelValue")?.at(-1)?.[0] as Record<string, Date>
        expect(emitted.change_at.toISOString()).toBe("2026-11-24T08:00:00.000Z")
    })

    test("shows a stored instant as the wall clock of the Time Zone preference", async () => {
        const wrapper = await mountDatetime("2026-11-24T08:00:00.000Z")
        const shown = wrapper.findComponent({name: "ElDatePicker"}).props("modelValue") as Date

        expect(shown).toEqual(new Date(2026, 10, 24, 9, 0, 0))
    })

    // Element Plus fills "Now" with the browser's wall clock, which the picker would then read in the preference zone.
    test("offers no Now button, which would be read in the browser zone", async () => {
        const wrapper = await mountDatetime()
        await wrapper.find("input").trigger("focus")
        await flushPromises()

        const labels = [...document.body.querySelectorAll<HTMLButtonElement>("button")]
            .filter((button) => button.style.display !== "none")
            .map((button) => button.textContent?.trim())
        expect(labels).toContain("OK")
        expect(labels).not.toContain("Now")
    })
})
