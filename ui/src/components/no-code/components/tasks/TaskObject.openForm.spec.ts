import {describe, test, expect, vi} from "vitest"
import {nextTick, ref} from "vue"
import {flushPromises} from "@vue/test-utils"
import KestraDesignSystem from "@kestra-io/design-system"
import TaskObject from "./TaskObject.vue"
import {FULL_SCHEMA_INJECTION_KEY} from "../../injectionKeys"
import {i18nMount} from "../../../../../tests/unit/i18nMount"

function mountForm(schema: Record<string, any>, modelValue: Record<string, any>) {
    return i18nMount(TaskObject, {
        props: {schema, modelValue},
        global: {
            plugins: [KestraDesignSystem],
            provide: {[FULL_SCHEMA_INJECTION_KEY as symbol]: ref({definitions: {}})},
        },
    })
}

describe("TaskObject opening a form", () => {
    test.each([
        ["plain", {type: "boolean", default: false}],
        ["anyOf", {anyOf: [{type: "boolean"}, {type: "string"}], default: false}],
        ["dynamic", {type: "boolean", default: false, $dynamic: true}],
        ["pebble", {type: "boolean", default: false}, "{{ inputs.flag }}"],
    ])("does not rewrite an invalid boolean (%s)", async (_name, field, value = "sometimes") => {
        const wrapper = mountForm(
            {type: "object", properties: {allowConcurrent: field}},
            {allowConcurrent: value},
        )
        await vi.waitFor(() => expect(wrapper.find("input[role=switch]").exists()).toBe(true))
        await flushPromises()
        await nextTick()

        expect(wrapper.emitted("update:modelValue")).toBeUndefined()
        expect((wrapper.find("input[role=switch]").element as HTMLInputElement).checked).toBe(false)
    })
})
