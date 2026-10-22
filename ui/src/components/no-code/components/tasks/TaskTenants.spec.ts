
import {describe, expect, test} from "vitest"
import {i18nMount} from "../../../../../tests/unit/i18nMount"
import {computed} from "vue"
import TaskTenants from "./TaskTenants.vue"
import {TENANTS_INJECTION_KEY} from "../../injectionKeys"

const tenants = [
    {id: "tenant-1", name: "Tenant One"},
    {id: "tenant-2", name: "Tenant Two"},
    {id: "tenant-3", name: "Tenant Three"},
    {id: "tenant-4", name: "Tenant Four"},
    {id: "tenant-5", name: "Tenant Five"},
]

function mountTaskTenants(
    props: Record<string, unknown> = {},
    provideTenants = false,
) {
    return i18nMount(TaskTenants, {
        props,
        global: {
            provide: provideTenants
                ? {
                    [TENANTS_INJECTION_KEY]: computed(() => tenants),
                }
                : {},
            stubs: {
                KsSelect: {
                    template: "<div><slot name='tag' /><slot /></div>",
                },
                KsTag: {
                    props: {
                        closable: Boolean,
                        type: String,
                    },
                    emits: ["close"],
                    template: `
                        <button
                            :class="closable ? 'tenant-tag' : 'summary-tag'"
                            @click="$emit('close')"
                        >
                            <slot />
                        </button>
                    `,
                },
                KsTooltip: {
                    template: "<div><slot name='content' /><slot /></div>",
                },
                KsOption: {
                    props: ["label", "value"],
                    template: `
                        <div class="tenant-option" :data-value="value">
                            {{ label }}
                        </div>
                    `,
                },
            },
        },
    })
}

describe("TaskTenants", () => {
    test("shows all selected tenants when there are three or fewer", () => {
        const wrapper = mountTaskTenants({
            modelValue: ["tenant-1", "tenant-2", "tenant-3"],
        })

        expect(wrapper.findAll(".tenant-tag").map(tag => tag.text())).toEqual([
            "tenant-1",
            "tenant-2",
            "tenant-3",
        ])
        expect(wrapper.find(".summary-tag").exists()).toBe(false)
    })

    test("shows three tenant tags and summarizes the remaining tenants", () => {
        const wrapper = mountTaskTenants({
            modelValue: [
                "tenant-1",
                "tenant-2",
                "tenant-3",
                "tenant-4",
                "tenant-5",
            ],
        })

        expect(wrapper.findAll(".tenant-tag")).toHaveLength(3)
        expect(wrapper.findAll(".tenant-tag").map(tag => tag.text())).toEqual([
            "tenant-1",
            "tenant-2",
            "tenant-3",
        ])
        expect(wrapper.find(".summary-tag").text()).toBe("+2")
        expect(wrapper.text()).toContain("tenant-4")
        expect(wrapper.text()).toContain("tenant-5")
    })

    test("renders no selected tenant tags for an empty selection", () => {
        const wrapper = mountTaskTenants({modelValue: []})

        expect(wrapper.findAll(".tenant-tag")).toHaveLength(0)
        expect(wrapper.find(".summary-tag").exists()).toBe(false)
    })

    test("defaults to an empty selection when no model value is provided", () => {
        const wrapper = mountTaskTenants()

        expect(wrapper.findAll(".tenant-tag")).toHaveLength(0)
        expect(wrapper.find(".summary-tag").exists()).toBe(false)
    })

    test("renders options from the provided tenant list", () => {
        const wrapper = mountTaskTenants({}, true)

        expect(wrapper.findAll(".tenant-option")).toHaveLength(5)
        expect(wrapper.find("[data-value='tenant-1']").text()).toBe("Tenant One")
    })

    test("handles a missing tenant provider", () => {
        const wrapper = mountTaskTenants()

        expect(wrapper.findAll(".tenant-option")).toHaveLength(0)
    })

    test("removes a selected tenant when its tag is closed", async () => {
        const wrapper = mountTaskTenants({
            modelValue: ["tenant-1", "tenant-2"],
        })

        await wrapper.find(".tenant-tag").trigger("click")

        expect(wrapper.emitted("update:modelValue")).toEqual([
            [["tenant-2"]],
        ])
    })
})
