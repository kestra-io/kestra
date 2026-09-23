import {describe, test, expect} from "vitest"
import {ref} from "vue"
import KestraDesignSystem from "@kestra-io/design-system"
import TaskComplex from "../../../../../src/components/no-code/components/tasks/TaskComplex.vue"
import TaskObject from "../../../../../src/components/no-code/components/tasks/TaskObject.vue"
import {FULL_SCHEMA_INJECTION_KEY} from "../../../../../src/components/no-code/injectionKeys"

import {i18nMount} from "../../../i18nMount"

const CHECK_DEFINITION = "io.kestra.core.models.flows.check.Check"

const CHECKS_SHAPED_SCHEMA = {
    allOf: [
        {$ref: `#/definitions/${CHECK_DEFINITION}`},
        {$dynamic: false},
    ],
}

const FULL_SCHEMA = {
    $ref: "#/definitions/io.kestra.core.models.flows.Flow",
    definitions: {
        [CHECK_DEFINITION]: {
            type: "object",
            properties: {
                when: {type: "string", minLength: 1},
                message: {type: "string", minLength: 1},
                style: {type: "string", enum: ["ERROR", "SUCCESS", "WARNING", "INFO"], default: "INFO"},
                behavior: {type: "string", enum: ["BLOCK_EXECUTION", "FAIL_EXECUTION", "CREATE_EXECUTION"], default: "BLOCK_EXECUTION"},
            },
            required: ["message", "when"],
        },
    },
}

function mountTaskComplex() {
    return i18nMount(TaskComplex, {
        props: {
            schema: CHECKS_SHAPED_SCHEMA,
        },
        global: {
            plugins: [KestraDesignSystem],
            provide: {
                [FULL_SCHEMA_INJECTION_KEY as symbol]: ref(FULL_SCHEMA),
            },
        },
    })
}

describe("TaskComplex (allOf $ref merge)", () => {
    test("carries the merged required array down to TaskObject for a Checks-shaped schema", () => {
        const wrapper = mountTaskComplex()
        const taskObject = wrapper.findComponent(TaskObject)

        expect(taskObject.exists()).toBe(true)
        expect(taskObject.props("schema")?.required).toEqual(expect.arrayContaining(["message", "when"]))
    })
})
