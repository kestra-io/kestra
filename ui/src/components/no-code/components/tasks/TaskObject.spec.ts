import {afterAll, describe, expect, test} from "vitest"
import KestraDesignSystem from "@kestra-io/design-system"
import TaskObject from "./TaskObject.vue"
import {BLOCK_COMPONENT_OVERRIDE_INJECTION_KEY} from "../../injectionKeys"
import {taskComponentsReady} from "./useBlockComponent"

import {i18nMount} from "../../../../../tests/unit/i18nMount"

afterAll(() => taskComponentsReady())

describe("TaskObject", () => {
    test("leaves out a field the edition hides for the edited block", () => {
        const wrapper = i18nMount(TaskObject, {
            props: {
                merge: true,
                schema: {properties: {label: {type: "string"}, threshold: {type: "string"}}},
                modelValue: {kind: "simple"},
            },
            global: {
                plugins: [KestraDesignSystem],
                provide: {
                    [BLOCK_COMPONENT_OVERRIDE_INJECTION_KEY as symbol]: (fieldKey: string, task: {kind?: string}) =>
                        fieldKey === "threshold" && task.kind === "simple" ? null : undefined,
                },
            },
        })

        expect(wrapper.find("[data-test='field-label']").exists()).toBe(true)
        expect(wrapper.find("[data-test='field-threshold']").exists()).toBe(false)
    })
})
