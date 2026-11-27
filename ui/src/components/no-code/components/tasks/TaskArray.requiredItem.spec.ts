import {afterAll, describe, expect, test} from "vitest"
import {computed} from "vue"
import {flushPromises} from "@vue/test-utils"
import {createPinia} from "pinia"
import TaskArray from "./TaskArray.vue"
import {FULL_SCHEMA_INJECTION_KEY, SCHEMA_DEFINITIONS_INJECTION_KEY} from "../../injectionKeys"
import {taskComponentsReady} from "./useBlockComponent"
import {i18nMount} from "../../../../../tests/unit/i18nMount"

const definitions = {
    Assertion: {
        type: "object",
        properties: {value: {$dynamic: true}, contains: {type: "string", $dynamic: true}},
        required: ["value"],
    },
}

function mountArray(required: boolean) {
    return i18nMount(TaskArray, {
        props: {
            root: "assertions",
            required,
            schema: {type: "array", items: {$ref: "#/definitions/Assertion"}},
        },
        global: {
            plugins: [createPinia()],
            stubs: {KsEditor: {template: "<textarea />"}},
            provide: {
                [FULL_SCHEMA_INJECTION_KEY as symbol]: computed(() => ({definitions})),
                [SCHEMA_DEFINITIONS_INJECTION_KEY as symbol]: computed(() => definitions),
            },
        },
    })
}

afterAll(() => taskComponentsReady())

describe("TaskArray object item", () => {
    test("flags the required fields of the blank item a required array starts with", async () => {
        const wrapper = mountArray(true)
        await flushPromises()

        expect(wrapper.find("[data-test='field-value']").attributes("data-required-path")).toBe("assertions[0].value")
    })

    test("flags the required fields of an item the user adds", async () => {
        const wrapper = mountArray(false)
        await flushPromises()
        await wrapper.find("button.add-value-btn").trigger("click")
        await flushPromises()

        expect(wrapper.find("[data-test='field-value']").attributes("data-required-path")).toBe("assertions[0].value")
    })
})
