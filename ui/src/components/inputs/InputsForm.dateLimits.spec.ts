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

const flow = {namespace: "company.team", id: "limits"} as Flow

async function mountDate(type: "DATE" | "DATETIME", limits: Record<string, unknown>) {
    const input = {id: "when", type, required: false, ...limits}
    useExecutionsStore().validateExecution = vi.fn(() => Promise.resolve({
        checks: [],
        inputs: [{enabled: true, isDefault: false, value: undefined, input}],
    }))

    const wrapper = i18nMount(InputsForm, {
        global: {plugins: [KestraDesignSystem]},
        props: {flow, initialInputs: [input] as InputMetaData[]},
        locales: en,
    })
    await flushPromises()

    const disabledDate = wrapper.findComponent({name: "KsDatePicker"}).props("disabledDate") as (date: Date) => boolean
    return {
        isDisabled: (year: number, month: number, day: number) => disabledDate(new Date(year, month - 1, day)),
        hint: wrapper.find(".hint"),
    }
}

describe("InputsForm date limits", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    afterEach(() => {
        document.body.innerHTML = ""
    })

    test("disables days outside after and before on a DATE input, keeping both boundary days", async () => {
        const {isDisabled, hint} = await mountDate("DATE", {after: "2026-10-01", before: "2026-10-31"})

        expect(isDisabled(2026, 9, 30)).toBe(true)
        expect(isDisabled(2026, 10, 1)).toBe(false)
        expect(isDisabled(2026, 10, 31)).toBe(false)
        expect(isDisabled(2026, 11, 1)).toBe(true)
        expect(hint.text()).toBe("Earliest value is 2026-10-01, latest value is 2026-10-31.")
    })

    test("applies a single limit on its own", async () => {
        const {isDisabled, hint} = await mountDate("DATE", {after: "2026-10-01"})

        expect(isDisabled(2026, 9, 30)).toBe(true)
        expect(isDisabled(2030, 1, 1)).toBe(false)
        expect(hint.text()).toBe("Earliest value is 2026-10-01.")
    })

    test("keeps the boundary days selectable on a DATETIME input", async () => {
        const after = new Date(2026, 9, 1, 12, 0).toISOString()
        const before = new Date(2026, 9, 31, 12, 0).toISOString()
        const {isDisabled, hint} = await mountDate("DATETIME", {after, before})

        expect(isDisabled(2026, 9, 30)).toBe(true)
        expect(isDisabled(2026, 10, 1)).toBe(false)
        expect(isDisabled(2026, 10, 31)).toBe(false)
        expect(isDisabled(2026, 11, 1)).toBe(true)
        expect(hint.exists()).toBe(true)
    })

    test("leaves the picker unrestricted without limits", async () => {
        const {isDisabled, hint} = await mountDate("DATE", {})

        expect(isDisabled(1970, 1, 1)).toBe(false)
        expect(isDisabled(2100, 12, 31)).toBe(false)
        expect(hint.exists()).toBe(false)
    })
})