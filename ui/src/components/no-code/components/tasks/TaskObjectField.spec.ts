import {describe, test, expect} from "vitest"
import {ref} from "vue"
import KestraDesignSystem from "@kestra-io/design-system"
import TaskObjectField from "./TaskObjectField.vue"
import {FIELD_VALIDATION_ERRORS_INJECTION_KEY, PLUGIN_DEFAULTS_INJECTION_KEY} from "../../injectionKeys"

import {i18nMount} from "../../../../../tests/unit/i18nMount"

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
