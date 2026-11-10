import {describe, test, expect} from "vitest"
import CustomizeFilters from "./CustomizeFilters.vue"
import {Comparators, type FilterConfiguration} from "../utils/filterTypes"
import {i18nMount} from "../../../../../../tests/units/i18nMount"

const configuration = {
    keys: [
        {key: "namespace", label: "Namespace", description: "Filter by namespace", comparators: [Comparators.IN], valueType: "multi-select"},
        {key: "state", label: "State", description: "Execution state", comparators: [Comparators.IN], valueType: "multi-select"},
        {key: "flowId", label: "Flow", description: "Filter by flow id", comparators: [Comparators.EQUALS], valueType: "text"},
    ],
} as FilterConfiguration

const mountPanel = () => i18nMount(CustomizeFilters, {
    props: {configuration, appliedFilters: []},
    attachTo: document.body,
})

const visibleLabels = (wrapper: ReturnType<typeof mountPanel>) =>
    wrapper.findAll(".item .label").map((label) => label.text())

describe("CustomizeFilters", () => {
    test("shouldNarrowKeysByLabelOrDescriptionWhenTyping", async () => {
        const wrapper = mountPanel()
        const input = wrapper.find("input")

        await input.setValue("STATE")
        expect(visibleLabels(wrapper)).toEqual(["State"])

        await input.setValue("flow id")
        expect(visibleLabels(wrapper)).toEqual(["Flow"])

        await input.setValue("nothing")
        expect(visibleLabels(wrapper)).toEqual([])
        expect(wrapper.text()).toContain("filter.no options found")

        wrapper.unmount()
    })

    test("shouldAddFirstMatchWhenPressingEnter", async () => {
        const wrapper = mountPanel()
        const input = wrapper.find("input")

        await input.setValue("filter by")
        await input.trigger("keydown", {key: "Enter"})

        const added = wrapper.emitted("add-filter")
        expect(added).toHaveLength(1)
        expect(added![0][0]).toMatchObject({key: "namespace"})

        wrapper.unmount()
    })

    test("shouldIgnoreEnterWhenTheSearchIsEmptyOrAnImeCandidateIsConfirmed", async () => {
        const wrapper = mountPanel()
        const input = wrapper.find("input")

        await input.trigger("keydown", {key: "Enter"})
        await input.setValue("filter by")
        await input.trigger("keydown", {key: "Enter", isComposing: true})

        expect(wrapper.emitted("add-filter")).toBeUndefined()

        wrapper.unmount()
    })

    test("shouldFocusSearchWhenOpened", async () => {
        const wrapper = mountPanel()
        await new Promise((resolve) => setTimeout(resolve))

        expect(document.activeElement).toBe(wrapper.find("input").element)

        wrapper.unmount()
    })
})
