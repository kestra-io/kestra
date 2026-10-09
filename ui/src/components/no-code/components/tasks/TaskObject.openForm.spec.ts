import {describe, test, expect} from "vitest"
import {ref} from "vue"
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
    ])("does not rewrite an invalid boolean (%s)", async (_name, field) => {
        const wrapper = mountForm(
            {type: "object", properties: {allowConcurrent: field}},
            {allowConcurrent: "sometimes"},
        )
        await new Promise(resolve => setTimeout(resolve, 50))

        expect(wrapper.emitted("update:modelValue")).toBeUndefined()
    })
})
