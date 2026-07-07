import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "../../../../src/index"
import FilterComparatorSelect from "../../../../src/components/Data/KsDataTable/filter/layout/FilterComparatorSelect.vue"
import {Comparators} from "../../../../src/components/Data/KsDataTable/filter/utils/filterTypes"

const i18n = createI18n({legacy: false, locale: "en", messages: {en: {}}})

const mountComparator = (
    filterKey: {comparators: Comparators[]; comparatorLabels?: Partial<Record<Comparators, string>>},
    selectedComparator: Comparators,
    shouldShowComparator = true,
) =>
    mount(FilterComparatorSelect, {
        props: {shouldShowComparator, selectedComparator, filterKey},
        global: {plugins: [i18n, KestraDesignSystem]},
    })

describe("FilterComparatorSelect", () => {
    test("shows no operator control while the comparator is hidden", () => {
        const wrapper = mountComparator(
            {comparators: [Comparators.GREATER_THAN_OR_EQUAL_TO, Comparators.LESS_THAN_OR_EQUAL_TO]},
            Comparators.GREATER_THAN_OR_EQUAL_TO,
            false,
        )

        expect(wrapper.findAll("button")).toHaveLength(0)
    })

    test("offers a few comparators as a toggle group, glyphs first, with the selected one pressed", () => {
        // Log-level filter: ≥ / ≤ both have glyphs and there are only two of them.
        const wrapper = mountComparator(
            {
                comparators: [Comparators.GREATER_THAN_OR_EQUAL_TO, Comparators.LESS_THAN_OR_EQUAL_TO],
                comparatorLabels: {
                    [Comparators.GREATER_THAN_OR_EQUAL_TO]: "At or Above",
                    [Comparators.LESS_THAN_OR_EQUAL_TO]: "At or Below",
                },
            },
            Comparators.GREATER_THAN_OR_EQUAL_TO,
        )

        expect(wrapper.find("[role='group']").exists()).toBe(true)
        const options = wrapper.findAll("button")
        expect(options.map(b => b.text())).toEqual(["≥", "≤"])

        // Selection is conveyed to assistive tech, and each glyph keeps a readable label.
        expect(options[0].attributes("aria-pressed")).toBe("true")
        expect(options[1].attributes("aria-pressed")).toBe("false")
        expect(options[0].attributes("aria-label")).toBe("At or Above")
        expect(options[1].attributes("aria-label")).toBe("At or Below")
    })

    test("picking a comparator emits the selection", async () => {
        const wrapper = mountComparator(
            {comparators: [Comparators.GREATER_THAN_OR_EQUAL_TO, Comparators.LESS_THAN_OR_EQUAL_TO]},
            Comparators.GREATER_THAN_OR_EQUAL_TO,
        )

        await wrapper.findAll("button")[1].trigger("click")

        expect(wrapper.emitted("update:selectedComparator")?.[0]).toEqual([Comparators.LESS_THAN_OR_EQUAL_TO])
    })

    test("falls back to text labels for a few glyph-less comparators", () => {
        const wrapper = mountComparator(
            {comparators: [Comparators.IN, Comparators.NOT_IN]},
            Comparators.IN,
        )

        expect(wrapper.find("[role='group']").exists()).toBe(true)
        expect(wrapper.findAll("button").map(b => b.text())).toEqual(["In", "Not In"])
    })

    test("collapses into a single dropdown when more than three comparators are offered", () => {
        const wrapper = mountComparator(
            {
                comparators: [
                    Comparators.EQUALS,
                    Comparators.NOT_EQUALS,
                    Comparators.GREATER_THAN,
                    Comparators.LESS_THAN,
                ],
            },
            Comparators.EQUALS,
        )

        // No toggle group; a single trigger surfaces the current comparator instead.
        expect(wrapper.find("[role='group']").exists()).toBe(false)
        const trigger = wrapper.find("button")
        expect(trigger.exists()).toBe(true)
        expect(trigger.attributes("aria-pressed")).toBeUndefined()
        expect(trigger.text()).toContain("Equals")
    })
})
