import {afterAll, describe, test, expect} from "vitest"
import KestraDesignSystem from "@kestra-io/design-system"
import TaskObject from "./TaskObject.vue"

import {i18nMount} from "../../../../../tests/unit/i18nMount"
import {taskComponentsReady} from "./useBlockComponent"

const schema = {
    type: "object",
    properties: {
        value: {type: "string"},
        description: {type: "string"},
        equalTo: {type: "string", $group: "operators"},
        isNull: {type: "boolean", $group: "operators"},
    },
    required: ["value"],
    $requiredGroups: ["operators"],
}

function mountAssertion(modelValue: Record<string, unknown> | undefined) {
    return i18nMount(TaskObject, {
        props: {schema, modelValue, root: "assertions[0]"},
        global: {plugins: [KestraDesignSystem]},
    })
}

afterAll(() => taskComponentsReady())

describe("TaskObject required group", () => {
    test("marks the group as required and lists it before the optional properties", () => {
        const wrapper = mountAssertion({value: "x"})
        const titles = wrapper.findAll(".gh-title")

        expect(titles[0].text()).toBe("Operators")
        expect(titles[0].classes()).toContain("is-required")
        expect(titles.slice(1).every(title => !title.classes().includes("is-required"))).toBe(true)
    })

    test("flags the group while none of its properties is set", () => {
        const wrapper = mountAssertion({value: "x"})

        expect(wrapper.find("[data-test='group-required-missing']").isVisible()).toBe(true)
        expect(wrapper.find("[data-required-path='assertions[0].equalTo']").exists()).toBe(true)
    })

    test("stops flagging the group once one of its properties is set", () => {
        const wrapper = mountAssertion({value: "x", isNull: true})

        expect(wrapper.find("[data-test='group-required-missing']").exists()).toBe(false)
        expect(wrapper.find("[data-required-path]").exists()).toBe(false)
    })

    test("does not flag the group of an assertion that is not set", () => {
        const wrapper = mountAssertion(undefined)

        expect(wrapper.find("[data-test='group-required-missing']").exists()).toBe(false)
    })
})
