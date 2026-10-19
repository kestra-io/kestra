import {beforeAll, beforeEach, describe, test, expect} from "vitest"
import {ref} from "vue"
import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"
import TaskObjectField from "./TaskObjectField.vue"
import {PLUGIN_DEFAULTS_INJECTION_KEY} from "../../injectionKeys"

import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../../../tests/unit/i18nMount"

beforeAll(async () => {
    await import("./getTaskComponent")
})

beforeEach(() => {
    setActivePinia(createPinia())
})

async function mountField(pluginDefaults: Record<string, unknown>) {
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
            },
        },
    })
    await flushPromises()
    return wrapper
}

describe("TaskObjectField required-field indicator", () => {
    test("flags an unset required field as missing, with a locatable data-required-path", async () => {
        const wrapper = await mountField({})

        expect(wrapper.find("[data-test='field-required-missing']").exists()).toBe(true)
        expect(wrapper.find("[data-required-path='level']").exists()).toBe(true)
    })

    test("still flags an unset required field even when a flow pluginDefault would supply it, since pluginDefaults is a removed OSS keyword that injects nothing", async () => {
        const wrapper = await mountField({level: "WARN"})

        expect(wrapper.find("[data-test='field-required-missing']").exists()).toBe(true)
        expect(wrapper.find("[data-required-path='level']").exists()).toBe(true)
    })
})
