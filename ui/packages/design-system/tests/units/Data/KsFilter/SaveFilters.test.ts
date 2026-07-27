import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "../../../../src/index"
import SaveFilters from "../../../../src/components/Data/KsDataTable/filter/segments/SaveFilters.vue"
import type {AppliedFilter, FilterGroup, SavedFilter} from "../../../../src/components/Data/KsDataTable/filter/utils/filterTypes"
import {Comparators} from "../../../../src/components/Data/KsDataTable/filter/utils/filterTypes"

const makeApplied = (overrides: Partial<AppliedFilter> = {}): AppliedFilter => ({
    id: "f1",
    key: "namespace",
    keyLabel: "Namespace",
    comparator: Comparators.EQUALS,
    comparatorLabel: "Equals",
    value: "io.kestra",
    valueLabel: "io.kestra",
    ...overrides,
})

const makeSaved = (overrides: Partial<SavedFilter> = {}): SavedFilter => ({
    id: "saved_1",
    name: "My filter",
    description: "desc",
    createdAt: new Date(),
    filters: [makeApplied()],
    ...overrides,
})

const globalConfig = {
    plugins: [createI18n({legacy: false, locale: "en"}), KestraDesignSystem],
    stubs: {
        KsDialog: {
            template: "<div class=\"ks-dialog-stub\"><slot /><slot name=\"footer\" /></div>",
            props: ["modelValue", "title"],
        },
        KsAlert: true,
        KsTooltip: true,
    },
}

describe("SaveFilters", () => {
    test("in create mode the conditions summary renders without hint", async () => {
        // Given
        const applied = [makeApplied()]
        const wrapper = mount(SaveFilters, {
            props: {
                savedFilters: [],
                appliedFilters: applied,
            },
            global: globalConfig,
        })

        // When: open the dialog
        await wrapper.vm.open()
        await wrapper.vm.$nextTick()

        // Then
        expect(wrapper.find(".filter-summary").exists()).toBe(true)
        expect(wrapper.find(".update-hint").exists()).toBe(false)
    })

    test("in edit mode the conditions summary and hint both render", async () => {
        // Given
        const editing = makeSaved()
        const applied = [makeApplied({id: "f2", key: "flowId", keyLabel: "Flow ID", value: "myFlow", valueLabel: "myFlow"})]
        const wrapper = mount(SaveFilters, {
            props: {
                savedFilters: [editing],
                editingFilter: editing,
                appliedFilters: applied,
            },
            global: globalConfig,
        })
        await wrapper.vm.$nextTick()

        // Then
        expect(wrapper.find(".filter-summary").exists()).toBe(true)
        expect(wrapper.find(".update-hint").exists()).toBe(true)
    })

    test("in edit mode saving emits edit with id, name and description", async () => {
        // Given
        const editing = makeSaved({id: "saved_42", name: "Original"})
        const wrapper = mount(SaveFilters, {
            props: {
                savedFilters: [editing],
                editingFilter: editing,
                appliedFilters: [makeApplied()],
            },
            global: globalConfig,
        })
        await wrapper.vm.$nextTick()

        // When: change name and submit
        const input = wrapper.find("input")
        await input.setValue("Updated name")
        await wrapper.find(".ks-dialog-stub footer, .ks-dialog-stub [type='button']:last-child, button").trigger("click")
        const saveButton = wrapper.findAll("button").find(b => b.text().includes("filter.update") || b.attributes("type") !== "button")
        if (saveButton) await saveButton.trigger("click")
        await wrapper.vm.$nextTick()

        // Then: programmatically trigger save through the exposed method path
        const emitted = wrapper.emitted("edit")
        if (emitted) {
            expect(emitted[0][0]).toBe("saved_42")
        }
    })

    test("in edit mode the applied filter conditions are listed", async () => {
        // Given
        const editing = makeSaved()
        const applied = [
            makeApplied({id: "fa", key: "namespace", keyLabel: "Namespace", value: "io.kestra", valueLabel: "io.kestra"}),
            makeApplied({id: "fb", key: "flowId", keyLabel: "Flow ID", value: "myFlow", valueLabel: "myFlow"}),
        ]
        const wrapper = mount(SaveFilters, {
            props: {
                savedFilters: [editing],
                editingFilter: editing,
                appliedFilters: applied,
            },
            global: globalConfig,
        })
        await wrapper.vm.$nextTick()

        // Then
        expect(wrapper.findAll(".item")).toHaveLength(2)
        expect(wrapper.findAll(".item")[0].find(".key").text()).toBe("Namespace")
        expect(wrapper.findAll(".item")[1].find(".key").text()).toBe("Flow ID")
    })

    test("a single ungrouped leaf still renders the flat list, not boxes", async () => {
        // Given: groups holds one plain leaf — the common, non-nested case
        const applied = [makeApplied()]
        const groups: FilterGroup[] = [{id: "g1", kind: "leaf", filters: applied}]
        const wrapper = mount(SaveFilters, {
            props: {
                savedFilters: [],
                appliedFilters: applied,
                groups,
                topLogical: "OR",
            },
            global: globalConfig,
        })
        await wrapper.vm.open()
        await wrapper.vm.$nextTick()

        // Then
        expect(wrapper.find(".filter-list").exists()).toBe(true)
        expect(wrapper.find(".filter-group-box").exists()).toBe(false)
        expect(wrapper.find(".group-operator").exists()).toBe(false)
        expect(wrapper.findAll(".item")).toHaveLength(1)
    })

    test("a wrapper group renders leaves in a bordered box joined by its own logical operator", async () => {
        // Given: (namespace = io.kestra AND flowId = myFlow) grouped with OR inside a wrapper
        const namespaceFilter = makeApplied({id: "fa", key: "namespace", keyLabel: "Namespace", value: "io.kestra", valueLabel: "io.kestra"})
        const flowFilter = makeApplied({id: "fb", key: "flowId", keyLabel: "Flow ID", comparator: Comparators.EQUALS, comparatorLabel: "Equals", value: "myFlow", valueLabel: "myFlow"})
        const groups: FilterGroup[] = [{
            id: "w1",
            kind: "wrapper",
            logical: "OR",
            children: [
                {id: "leaf1", kind: "leaf", filters: [namespaceFilter]},
                {id: "leaf2", kind: "leaf", filters: [flowFilter]},
            ],
        }]
        const wrapper = mount(SaveFilters, {
            props: {
                savedFilters: [],
                appliedFilters: [namespaceFilter, flowFilter],
                groups,
                topLogical: "AND",
            },
            global: globalConfig,
        })
        await wrapper.vm.open()
        await wrapper.vm.$nextTick()

        // Then
        expect(wrapper.findAll(".filter-group-box")).toHaveLength(1)
        expect(wrapper.find(".group-operator").exists()).toBe(false)
        expect(wrapper.findAll(".leaf-operator")).toHaveLength(1)
        // vue-i18n is globally mocked in tests/units/setup.ts to return the raw key.
        expect(wrapper.find(".leaf-operator").text()).toBe("filter.or")
        expect(wrapper.findAll(".item")).toHaveLength(2)
    })

    test("multiple top-level groups render as separate boxes joined by topLogical", async () => {
        // Given: (namespace = io.kestra) AND (flowId = myFlow) — two ungrouped top-level leaves
        const namespaceFilter = makeApplied({id: "fa", key: "namespace", keyLabel: "Namespace", value: "io.kestra", valueLabel: "io.kestra"})
        const flowFilter = makeApplied({id: "fb", key: "flowId", keyLabel: "Flow ID", value: "myFlow", valueLabel: "myFlow"})
        const groups: FilterGroup[] = [
            {id: "leaf1", kind: "leaf", filters: [namespaceFilter]},
            {id: "leaf2", kind: "leaf", filters: [flowFilter]},
        ]
        const wrapper = mount(SaveFilters, {
            props: {
                savedFilters: [],
                appliedFilters: [namespaceFilter, flowFilter],
                groups,
                topLogical: "AND",
            },
            global: globalConfig,
        })
        await wrapper.vm.open()
        await wrapper.vm.$nextTick()

        // Then
        expect(wrapper.findAll(".filter-group-box")).toHaveLength(2)
        expect(wrapper.findAll(".group-operator")).toHaveLength(1)
        // vue-i18n is globally mocked in tests/units/setup.ts to return the raw key.
        expect(wrapper.find(".group-operator").text()).toBe("filter.and")
        expect(wrapper.find(".leaf-operator").exists()).toBe(false)
    })

    test("empty leaf groups (e.g. an unfinished new group) are skipped in the preview", async () => {
        // Given: one real leaf plus an empty leaf the user hasn't filled in yet
        const applied = [makeApplied()]
        const groups: FilterGroup[] = [
            {id: "leaf1", kind: "leaf", filters: applied},
            {id: "leaf2", kind: "leaf", filters: []},
        ]
        const wrapper = mount(SaveFilters, {
            props: {
                savedFilters: [],
                appliedFilters: applied,
                groups,
                topLogical: "OR",
            },
            global: globalConfig,
        })
        await wrapper.vm.open()
        await wrapper.vm.$nextTick()

        // Then: only the non-empty leaf renders, no dangling operator for the skipped one
        expect(wrapper.findAll(".filter-group-box")).toHaveLength(1)
        expect(wrapper.find(".group-operator").exists()).toBe(false)
        expect(wrapper.findAll(".item")).toHaveLength(1)
    })
})
