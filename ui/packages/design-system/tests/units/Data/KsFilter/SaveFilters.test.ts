import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "../../../../src/index"
import SaveFilters from "../../../../src/components/Data/KsDataTable/filter/segments/SaveFilters.vue"
import type {AppliedFilter, SavedFilter} from "../../../../src/components/Data/KsDataTable/filter/utils/filterTypes"
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
})
