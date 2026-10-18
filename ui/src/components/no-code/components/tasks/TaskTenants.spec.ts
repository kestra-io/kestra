import {describe, it, expect, beforeEach} from "vitest"
import {computed} from "vue"
import {createPinia, setActivePinia} from "pinia"
import {KsTag, KsTooltip, KsOption} from "@kestra-io/design-system"
import {i18nMount} from "../../../../../tests/unit/i18nMount"
import TaskTenants from "./TaskTenants.vue"
import {TENANTS_INJECTION_KEY} from "../../injectionKeys"

describe("TaskTenants.vue", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    it("three or fewer tenants all render as visible tags, with nothing hidden", () => {
        const wrapper = i18nMount(TaskTenants, {
            props: {
                modelValue: ["tenant-1", "tenant-2", "tenant-3"],
            },
        })

        const tags = wrapper.findAllComponents(KsTag)
        expect(tags).toHaveLength(3)
        expect(tags.map((t) => t.text())).toEqual(["tenant-1", "tenant-2", "tenant-3"])

        const tooltip = wrapper.findComponent(KsTooltip)
        expect(tooltip.exists()).toBe(false)
    })

    it("two tenants render both visible with no tooltip", () => {
        const wrapper = i18nMount(TaskTenants, {
            props: {
                modelValue: ["alpha", "beta"],
            },
        })

        const tags = wrapper.findAllComponents(KsTag)
        expect(tags).toHaveLength(2)
        expect(tags.map((t) => t.text())).toEqual(["alpha", "beta"])
        expect(wrapper.findComponent(KsTooltip).exists()).toBe(false)
    })

    it("more than three renders the first three visible and the rest hidden in a tooltip", () => {
        const wrapper = i18nMount(TaskTenants, {
            props: {
                modelValue: ["tenant-1", "tenant-2", "tenant-3", "tenant-4", "tenant-5"],
            },
        })

        const tags = wrapper.findAllComponents(KsTag)
        // 3 visible tags + 1 overflow counter tag inside tooltip
        expect(tags).toHaveLength(4)
        expect(tags.slice(0, 3).map((t) => t.text())).toEqual(["tenant-1", "tenant-2", "tenant-3"])

        const tooltip = wrapper.findComponent(KsTooltip)
        expect(tooltip.exists()).toBe(true)
        expect(tags[3].text()).toBe("+2")
    })

    it("an empty selection renders no tags", () => {
        const wrapper = i18nMount(TaskTenants, {
            props: {
                modelValue: [],
            },
        })

        expect(wrapper.findAllComponents(KsTag)).toHaveLength(0)
        expect(wrapper.findComponent(KsTooltip).exists()).toBe(false)
    })

    it("the model defaults to an empty array when nothing is bound", () => {
        const wrapper = i18nMount(TaskTenants)

        expect(wrapper.findAllComponents(KsTag)).toHaveLength(0)
        expect(wrapper.findComponent(KsTooltip).exists()).toBe(false)
    })

    it("uses the injected tenant list and falls back to an empty list when missing", () => {
        const mockTenants = [
            {id: "tenant-a", name: "Tenant A"},
            {id: "tenant-b"},
        ]

        const wrapperWithInjection = i18nMount(TaskTenants, {
            global: {
                provide: {
                    [TENANTS_INJECTION_KEY as symbol]: computed(() => mockTenants),
                },
            },
        })

        const options = wrapperWithInjection.findAllComponents(KsOption)
        expect(options).toHaveLength(2)
        expect(options[0].props("label")).toBe("Tenant A")
        expect(options[0].props("value")).toBe("tenant-a")
        expect(options[1].props("label")).toBe("tenant-b")
        expect(options[1].props("value")).toBe("tenant-b")

        const wrapperWithoutInjection = i18nMount(TaskTenants)
        expect(wrapperWithoutInjection.findAllComponents(KsOption)).toHaveLength(0)
    })

    it("removes a tag and updates modelValue when tag close is clicked", async () => {
        const wrapper = i18nMount(TaskTenants, {
            props: {
                modelValue: ["alpha", "beta", "gamma"],
            },
        })

        const tags = wrapper.findAllComponents(KsTag)
        await tags[1].vm.$emit("close")

        expect(wrapper.emitted("update:modelValue")).toEqual([[["alpha", "gamma"]]])
    })
})
