import {describe, expect, it, vi} from "vitest"
import {defineComponent, h} from "vue"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {createPinia} from "pinia"

vi.mock("vue-router", () => ({
    useRoute: () => ({name: "executions/list", query: {}}),
}))

import {useTriggerFilter} from "../../../../../src/components/filter/configurations/triggerFilter"

const i18n = createI18n({legacy: false, locale: "en", missingWarn: false, fallbackWarn: false, messages: {en: {}}})

function setup<T>(useComposable: () => T): T {
    let api!: T
    const Comp = defineComponent({
        setup() {
            api = useComposable()
            return () => h("div")
        },
    })
    mount(Comp, {global: {plugins: [i18n, createPinia()]}})
    return api
}

describe("trigger time range filter", () => {
    it("uses past relative dates for the last triggered date", async () => {
        const config = setup(() => useTriggerFilter())
        const valueProvider = config.value.keys.find(({key}) => key === "timeRange")?.valueProvider

        expect(valueProvider).toBeDefined()
        const values = await valueProvider!({meta: {dateFilter: "LAST_TRIGGERED_DATE"}})
        expect(values[0]?.label).toBe("datepicker.last5minutes")
    })
})
