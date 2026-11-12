import {afterAll, describe, test, expect} from "vitest"
import {defineComponent, h, ref} from "vue"
import KestraDesignSystem from "@kestra-io/design-system"
import TaskObjectField from "./TaskObjectField.vue"
import {BLOCK_COMPONENT_OVERRIDE_INJECTION_KEY, FIELD_VALIDATION_ERRORS_INJECTION_KEY, PLUGIN_DEFAULTS_INJECTION_KEY} from "../../injectionKeys"

import {i18nMount} from "../../../../../tests/unit/i18nMount"
import {taskComponentsReady} from "./useBlockComponent"

function mountField(pluginDefaults: Record<string, unknown>) {
    return i18nMount(TaskObjectField, {
        props: {
            schema: {type: "string"},
            fieldKey: "level",
            task: {},
            required: ["level"],
            modelValue: undefined,
        },
        global: {
            plugins: [KestraDesignSystem],
            provide: {
                [PLUGIN_DEFAULTS_INJECTION_KEY as symbol]: ref(pluginDefaults),
            },
        },
    })
}

afterAll(() => taskComponentsReady())

describe("TaskObjectField required-field indicator", () => {
    test("flags an unset required field as missing, with a locatable data-required-path", () => {
        const wrapper = mountField({})

        expect(wrapper.find("[data-test='field-required-missing']").exists()).toBe(true)
        expect(wrapper.find("[data-required-path='level']").exists()).toBe(true)
    })

    test("still flags an unset required field even when a flow pluginDefault would supply it, since pluginDefaults is a removed OSS keyword that injects nothing", () => {
        const wrapper = mountField({level: "WARN"})

        expect(wrapper.find("[data-test='field-required-missing']").exists()).toBe(true)
        expect(wrapper.find("[data-required-path='level']").exists()).toBe(true)
    })
})

describe("TaskObjectField located validation errors", () => {
    function mountArrayField(errors: Record<string, string[]>) {
        return i18nMount(TaskObjectField, {
            props: {
                schema: {type: "array", items: {type: "string"}},
                fieldKey: "stopAfter",
                task: {},
                modelValue: ["NOPE"],
            },
            global: {
                plugins: [KestraDesignSystem],
                provide: {
                    [FIELD_VALIDATION_ERRORS_INJECTION_KEY as symbol]: ref(new Map(Object.entries(errors))),
                },
            },
        })
    }

    test("shows an error addressed to the field itself", () => {
        const wrapper = mountArrayField({stopAfter: ["field error"]})

        expect(wrapper.text()).toContain("field error")
    })

    test("shows an error addressed to an array item under the array field", () => {
        const wrapper = mountArrayField({"stopAfter[0]": ["item error"]})

        expect(wrapper.text()).toContain("item error")
    })
})

describe("TaskObjectField component override", () => {
    const Grid = Object.assign(defineComponent({render: () => h("div", {"data-test": "grid"})}), {ksTaskName: "grid"})

    function mountWithOverride(task: Record<string, unknown>) {
        return i18nMount(TaskObjectField, {
            props: {schema: {type: "string"}, fieldKey: "defaults", task, modelValue: undefined},
            global: {
                plugins: [KestraDesignSystem],
                provide: {
                    [BLOCK_COMPONENT_OVERRIDE_INJECTION_KEY as symbol]: (fieldKey: string, parent: {type?: string}) =>
                        fieldKey === "defaults" && parent.type === "TABLE" ? Grid : undefined,
                },
            },
        })
    }

    test("renders the component the edition provides for the field", () => {
        expect(mountWithOverride({type: "TABLE"}).find("[data-test='grid']").exists()).toBe(true)
    })

    test("keeps the schema-driven component when the edition provides none for the field", () => {
        expect(mountWithOverride({type: "STRING"}).find("[data-test='grid']").exists()).toBe(false)
    })
})
