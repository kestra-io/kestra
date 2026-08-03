import {describe, test, expect} from "vitest"
import {nextTick} from "vue"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "../../../../src/index"
import FilterMultiSelect from "../../../../src/components/Data/KsDataTable/filter/layout/FilterMultiSelect.vue"
import {STATES} from "../../../../src/utils/state"

const i18n = createI18n({legacy: false, locale: "en", messages: {en: {expand: "Expand", collapse: "Collapse", filter: {state_group: {running: "Running", paused: "Paused", completed: "Completed", failed: "Failed", other: "Other"}}}}})
const globalConfig = {plugins: [i18n, KestraDesignSystem]}

const ALL_STATE_OPTIONS = Object.keys(STATES).map(state => ({value: state, label: state}))

const mountStateFilter = (modelValue: string[] = [], options = ALL_STATE_OPTIONS) =>
    mount(FilterMultiSelect, {
        props: {filterKey: "state", modelValue, options, searchable: true},
        global: globalConfig,
    })

const mountFlatFilter = (modelValue: string[] = []) =>
    mount(FilterMultiSelect, {
        props: {
            filterKey: "namespace",
            modelValue,
            options: [{value: "ns1", label: "ns1"}, {value: "ns2", label: "ns2"}],
            searchable: true,
        },
        global: globalConfig,
    })

describe("FilterMultiSelect — grouped state mode", () => {
    test("renders groups for filterKey='state', collapsed by default", () => {
        // Given / When
        const wrapper = mountStateFilter()

        // Then — group rows present
        const groups = wrapper.findAll(".state-group")
        expect(groups.length).toBeGreaterThan(0)

        // All groups are collapsed by default (no expanded class)
        groups.forEach(g => expect(g.classes()).not.toContain("state-group-expanded"))

        // Individual option rows are hidden
        expect(wrapper.findAll(".group-options").length).toBe(0)
    })

    test("does NOT render groups for filterKey !== 'state' (flat list)", () => {
        // Given / When
        const wrapper = mountFlatFilter()

        // Then — no group structure at all
        expect(wrapper.findAll(".state-group").length).toBe(0)
        // Flat option items present
        expect(wrapper.findAll(".option-item").length).toBe(2)
    })

    test("expand chevron reveals individual state rows", async () => {
        // Given
        const wrapper = mountStateFilter()
        const firstExpandBtn = wrapper.find(".group-expand-btn")
        expect(firstExpandBtn.exists()).toBe(true)
        expect(firstExpandBtn.attributes("aria-expanded")).toBe("false")

        // When
        await firstExpandBtn.trigger("click")

        // Then — first group now expanded
        const firstGroup = wrapper.find(".state-group")
        expect(firstGroup.classes()).toContain("state-group-expanded")
        expect(wrapper.find(".group-options").exists()).toBe(true)
        expect(firstExpandBtn.attributes("aria-expanded")).toBe("true")
    })

    test("group toggle selects whole bucket", async () => {
        // Given — Running group has 7 states
        const wrapper = mountStateFilter([])
        const firstToggle = wrapper.find(".group-toggle")

        // When — click the toggle (select)
        await firstToggle.trigger("click")

        // Then — all Running states emitted
        const emitted = wrapper.emitted("update:modelValue") as string[][]
        expect(emitted).toBeTruthy()
        const lastEmit = emitted.at(-1)![0] as unknown as string[]
        const runningStates = ["SUBMITTED", "CREATED", "RESTARTED", "QUEUED", "RUNNING", "RETRYING"]
        runningStates.forEach(s => expect(lastEmit).toContain(s))
    })

    test("group toggle deselects whole bucket when fully selected", async () => {
        // Given — all Running states already selected
        const runningStates = ["SUBMITTED", "CREATED", "RESTARTED", "QUEUED", "RUNNING", "RETRYING"]
        const wrapper = mountStateFilter(runningStates)
        const firstToggle = wrapper.find(".group-toggle")

        // When — click the toggle (deselect)
        await firstToggle.trigger("click")

        // Then — all Running states removed
        const emitted = wrapper.emitted("update:modelValue") as string[][]
        expect(emitted).toBeTruthy()
        const lastEmit = emitted.at(-1)![0] as unknown as string[]
        runningStates.forEach(s => expect(lastEmit).not.toContain(s))
    })

    test("group checkbox shows indeterminate when partially selected", async () => {
        // Given — only RUNNING selected (partial in Running group)
        const wrapper = mountStateFilter(["RUNNING"])

        // Then — first group toggle checkbox has indeterminate state
        const firstGroup = wrapper.find(".state-group")
        const checkbox = firstGroup.findComponent({name: "KsCheckbox"})
        expect(checkbox.exists()).toBe(true)
        expect(checkbox.props("indeterminate")).toBe(true)
        expect(checkbox.props("modelValue")).toBe(false)
    })

    test("count badge shows selected/total for a group", async () => {
        // Given — 2 Running states selected out of 6
        const wrapper = mountStateFilter(["RUNNING", "QUEUED"])

        // Then — first group (Running) count shows 2/6
        const countBadge = wrapper.find(".group-count")
        expect(countBadge.text()).toBe("2/6")
        expect(countBadge.classes()).toContain("active")
    })

    test("search auto-expands matching groups and hides non-matching", async () => {
        // Given
        const wrapper = mountStateFilter()
        const input = wrapper.find("input")

        // When — search for a Running-group state
        await input.setValue("RUNNING")
        await nextTick()

        // Then — matching group appears and is auto-expanded
        const groups = wrapper.findAll(".state-group")
        expect(groups.length).toBe(1)
        expect(groups[0].classes()).toContain("state-group-expanded")
        expect(wrapper.find(".group-options").exists()).toBe(true)
    })

    test("a single-state bucket renders as a plain row (no chevron)", () => {
        // Given — one unknown state forms a single-item "Other" bucket
        const opts = [...ALL_STATE_OPTIONS, {value: "CUSTOM", label: "CUSTOM"}]
        const wrapper = mountStateFilter([], opts)

        // Then — the single-state bucket is a plain row; the 4 multi-state buckets keep their chevrons
        expect(wrapper.findAll(".state-single").length).toBe(1)
        expect(wrapper.findAll(".group-expand-btn").length).toBe(4)
    })

    test("clearing search re-collapses groups", async () => {
        // Given — search expanded a group
        const wrapper = mountStateFilter()
        const input = wrapper.find("input")
        await input.setValue("RUNNING")
        await nextTick()

        // When — clear search
        await input.setValue("")
        await nextTick()

        // Then — groups still exist, no auto-expansion happens for empty query
        const groups = wrapper.findAll(".state-group")
        expect(groups.length).toBeGreaterThan(0)
    })

    test("flat filter still emits update:modelValue on option click", async () => {
        // Given
        const wrapper = mountFlatFilter([])
        const firstOption = wrapper.find(".option-item")

        // When
        await firstOption.trigger("click")

        // Then
        const emitted = wrapper.emitted("update:modelValue")
        expect(emitted).toBeTruthy()
        expect((emitted![0][0] as string[])).toContain("ns1")
    })
})
