import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"
import InputsForm from "../../../../src/components/inputs/InputsForm.vue"
import {useExecutionsStore} from "../../../../src/stores/executions"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}, name: "flow"}),
    useRouter: () => ({replace: vi.fn(), push: vi.fn()}),
}))

const globalConfig = {
    plugins: [
        createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false}),
        KestraDesignSystem,
    ],
}

const flow = {namespace: "company.team", id: "datetime_tz"} as any

// https://github.com/kestra-io/kestra-ee/issues/12807
describe("InputsForm DATETIME input time zone", () => {
    const originalTz = process.env.TZ

    beforeEach(() => {
        setActivePinia(createPinia())
        process.env.TZ = "Asia/Kolkata"
        localStorage.setItem("timezone", "Europe/Paris")
    })

    afterEach(() => {
        process.env.TZ = originalTz
        localStorage.removeItem("timezone")
        document.body.innerHTML = ""
    })

    async function mountDatetime(value?: string) {
        const input = {id: "change_at", type: "DATETIME", required: false}
        useExecutionsStore().validateExecution = vi.fn(() => Promise.resolve({
            status: 200,
            headers: {},
            data: {checks: [], inputs: [{enabled: true, isDefault: false, value, input}]},
        })) as any

        const wrapper = mount(InputsForm, {
            global: globalConfig,
            props: {flow, initialInputs: [input] as any},
        })
        await flushPromises()
        return wrapper
    }

    test("stores the typed value as a wall clock in the Time Zone preference, not the browser zone", async () => {
        const wrapper = await mountDatetime()

        // The picker emits a browser-local Date: 09:00 as typed by the user.
        wrapper.findComponent({name: "KsDatePicker"}).vm.$emit("update:modelValue", new Date(2026, 10, 24, 9, 0, 0))
        await flushPromises()

        const emitted = wrapper.emitted("update:modelValue")?.at(-1)?.[0] as Record<string, Date>
        expect(emitted.change_at.toISOString()).toBe("2026-11-24T08:00:00.000Z")
    })

    test("shows a stored instant as the wall clock of the Time Zone preference", async () => {
        const wrapper = await mountDatetime("2026-11-24T08:00:00.000Z")
        const shown = wrapper.findComponent({name: "KsDatePicker"}).props("modelValue") as Date

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
