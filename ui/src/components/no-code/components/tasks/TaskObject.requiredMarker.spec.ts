import {describe, test, expect} from "vitest"
import KestraDesignSystem from "@kestra-io/design-system"
import TaskObject from "./TaskObject.vue"

import {i18nMount} from "../../../../../tests/unit/i18nMount"

const schema = {
    type: "object",
    properties: {
        enabled: {type: "boolean"},
        ttl: {type: "string"},
    },
    required: ["enabled"],
}

function mountObject(modelValue: Record<string, unknown> | null | undefined) {
    return i18nMount(TaskObject, {
        props: {schema, modelValue, root: "taskCache"},
        global: {plugins: [KestraDesignSystem]},
    })
}

describe("TaskObject required-field indicator", () => {
    test("does not flag a required field inside an optional object that is not set", () => {
        const wrapper = mountObject(undefined)

        expect(wrapper.find("[data-test='field-required-missing']").exists()).toBe(false)
        expect(wrapper.find("[data-required-path]").exists()).toBe(false)
    })

    test("does not flag a required field inside an object that is null", () => {
        const wrapper = mountObject(null)

        expect(wrapper.find("[data-test='field-required-missing']").exists()).toBe(false)
        expect(wrapper.find("[data-required-path]").exists()).toBe(false)
    })

    test("flags a required field left unset inside an object that is set", () => {
        const wrapper = mountObject({ttl: "PT1H"})

        expect(wrapper.find("[data-required-path='taskCache.enabled']").exists()).toBe(true)
    })
})
