import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "../../../../src/index"
import FilterComparatorSelect from "../../../../src/components/Data/KsDataTable/filter/layout/FilterComparatorSelect.vue"
import {Comparators} from "../../../../src/components/Data/KsDataTable/filter/utils/filterTypes"

const i18n = createI18n({legacy: false, locale: "en", messages: {en: {}}})
const globalConfig = {plugins: [i18n, KestraDesignSystem]}

describe("FilterComparatorSelect", () => {
    test("renders nothing when shouldShowComparator is false", () => {
        const wrapper = mount(FilterComparatorSelect, {
            props: {
                shouldShowComparator: false,
                selectedComparator: Comparators.GREATER_THAN_OR_EQUAL_TO,
                filterKey: {comparators: [Comparators.GREATER_THAN_OR_EQUAL_TO, Comparators.LESS_THAN_OR_EQUAL_TO]},
            },
            global: globalConfig,
        })
        expect(wrapper.find(".comparator-segments").exists()).toBe(false)
        expect(wrapper.find(".comparator-dropdown").exists()).toBe(false)
    })

    test("renders glyph segmented toggle for ordered comparators with glyphs (≤ 3)", () => {
        // Given — log-level filter: GTE/LTE both have glyphs and count is 2 (≤ 3)
        const wrapper = mount(FilterComparatorSelect, {
            props: {
                shouldShowComparator: true,
                selectedComparator: Comparators.GREATER_THAN_OR_EQUAL_TO,
                filterKey: {
                    comparators: [Comparators.GREATER_THAN_OR_EQUAL_TO, Comparators.LESS_THAN_OR_EQUAL_TO],
                    comparatorLabels: {
                        [Comparators.GREATER_THAN_OR_EQUAL_TO]: "At or Above",
                        [Comparators.LESS_THAN_OR_EQUAL_TO]: "At or Below",
                    },
                },
            },
            global: globalConfig,
        })

        // Then — segmented toggle is rendered
        const segments = wrapper.find(".comparator-segments")
        expect(segments.exists()).toBe(true)
        expect(wrapper.find(".comparator-dropdown").exists()).toBe(false)

        const buttons = wrapper.findAll(".comparator-segment")
        expect(buttons).toHaveLength(2)
        expect(buttons[0].text()).toBe("≥")
        expect(buttons[1].text()).toBe("≤")

        // Active segment has the active class
        expect(buttons[0].classes()).toContain("active")
        expect(buttons[1].classes()).not.toContain("active")

        // aria-pressed reflects selection
        expect(buttons[0].attributes("aria-pressed")).toBe("true")
        expect(buttons[1].attributes("aria-pressed")).toBe("false")

        // Accessible label uses custom comparatorLabels (also shown as a tooltip on hover)
        expect(buttons[0].attributes("aria-label")).toBe("At or Above")
        expect(buttons[1].attributes("aria-label")).toBe("At or Below")
    })

    test("emits update:selectedComparator when a segment is clicked", async () => {
        // Given
        const wrapper = mount(FilterComparatorSelect, {
            props: {
                shouldShowComparator: true,
                selectedComparator: Comparators.GREATER_THAN_OR_EQUAL_TO,
                filterKey: {
                    comparators: [Comparators.GREATER_THAN_OR_EQUAL_TO, Comparators.LESS_THAN_OR_EQUAL_TO],
                },
            },
            global: globalConfig,
        })

        // When — click the second segment (LTE)
        const buttons = wrapper.findAll(".comparator-segment")
        await buttons[1].trigger("click")

        // Then
        const emitted = wrapper.emitted("update:selectedComparator")
        expect(emitted).toBeTruthy()
        expect(emitted![0]).toEqual([Comparators.LESS_THAN_OR_EQUAL_TO])
    })

    test("renders text segments for few non-glyph comparators (IN/NOT_IN)", () => {
        // Given — state filter: IN/NOT_IN have no glyphs but count ≤ 3 → text segments (no dropdown)
        const wrapper = mount(FilterComparatorSelect, {
            props: {
                shouldShowComparator: true,
                selectedComparator: Comparators.IN,
                filterKey: {
                    comparators: [Comparators.IN, Comparators.NOT_IN],
                },
            },
            global: globalConfig,
        })

        // Then — segmented toggle with text labels, no dropdown select
        expect(wrapper.find(".comparator-segments").exists()).toBe(true)
        expect(wrapper.find(".comparator-dropdown").exists()).toBe(false)
        const buttons = wrapper.findAll(".comparator-segment")
        expect(buttons).toHaveLength(2)
        expect(buttons[0].text()).toBe("In")
        expect(buttons[1].text()).toBe("Not In")
    })

    test("renders a compact dropdown when more than 3 comparators are present", () => {
        // Given — 4 comparators exceed the 3-item segmented threshold
        const wrapper = mount(FilterComparatorSelect, {
            props: {
                shouldShowComparator: true,
                selectedComparator: Comparators.EQUALS,
                filterKey: {
                    comparators: [
                        Comparators.EQUALS,
                        Comparators.NOT_EQUALS,
                        Comparators.GREATER_THAN,
                        Comparators.LESS_THAN,
                    ],
                },
            },
            global: globalConfig,
        })

        // Then — falls back to the compact dropdown trigger, not the segmented toggle
        expect(wrapper.find(".comparator-trigger").exists()).toBe(true)
        expect(wrapper.find(".comparator-segments").exists()).toBe(false)
    })
})
