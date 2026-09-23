import {describe, test, expect} from "vitest"
import {reactive, ref} from "vue"
import KestraDesignSystem from "@kestra-io/design-system"
import TaskObjectField from "../../../../../src/components/no-code/components/tasks/TaskObjectField.vue"
import {
    PLUGIN_DEFAULTS_INJECTION_KEY,
    REQUIRED_FIELDS_TRACKER_INJECTION_KEY,
} from "../../../../../src/components/no-code/injectionKeys"

import {i18nMount} from "../../../i18nMount"

function mountField(pluginDefaults: Record<string, unknown>) {
    const tracker = reactive(new Map<string, string>())
    const wrapper = i18nMount(TaskObjectField, {
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
                [REQUIRED_FIELDS_TRACKER_INJECTION_KEY as symbol]: tracker,
            },
        },
    })
    return {wrapper, tracker}
}

describe("TaskObjectField required-field tracking", () => {
    test("flags an unset required field as missing and registers it in the tracker", () => {
        const {wrapper, tracker} = mountField({})

        expect(wrapper.find("[data-test='field-required-missing']").exists()).toBe(true)
        expect(wrapper.find("[data-required-path='level']").exists()).toBe(true)
        expect(tracker.get("level")).toBe("level")
    })

    test("still flags an unset required field even when a flow pluginDefault would supply it, since pluginDefaults is a removed OSS keyword that injects nothing", () => {
        const {wrapper, tracker} = mountField({level: "WARN"})

        expect(wrapper.find("[data-test='field-required-missing']").exists()).toBe(true)
        expect(wrapper.find("[data-required-path='level']").exists()).toBe(true)
        expect(tracker.get("level")).toBe("level")
    })
})
