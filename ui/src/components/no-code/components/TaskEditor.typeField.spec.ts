import {afterAll, describe, expect, test, vi} from "vitest"
import {computed} from "vue"
import {flushPromises} from "@vue/test-utils"
import {createPinia} from "pinia"
import {
    BLOCK_SCHEMA_PATH_INJECTION_KEY,
    FULL_SCHEMA_INJECTION_KEY,
    PARENT_PATH_INJECTION_KEY,
    SCHEMA_DEFINITIONS_INJECTION_KEY,
} from "../injectionKeys"
import {taskComponentsReady} from "./tasks/useBlockComponent"

vi.mock("../../../stores/plugins", async (importOriginal) => ({
    ...await importOriginal<typeof import("../../../stores/plugins")>(),
    usePluginsStore: () => ({
        icons: {},
        allTypes: [],
        load: vi.fn(() => Promise.resolve({})),
        updateDocumentation: vi.fn(),
    }),
}))

vi.mock("../../../stores/playground", () => ({
    usePlaygroundStore: () => ({enabled: false}),
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {}}),
}))

import TaskEditor from "./TaskEditor.vue"
import {i18nMount} from "../../../../tests/unit/i18nMount"

afterAll(() => taskComponentsReady())

const definitions = {
    Suite: {
        type: "object",
        properties: {
            testCases: {type: "array", items: {$ref: "#/definitions/UnitTest"}},
            tasks: {type: "array", items: {anyOf: [{$ref: "#/definitions/Log"}]}},
        },
    },
    UnitTest: {
        type: "object",
        properties: {id: {type: "string"}, type: {type: "string"}},
        required: ["id", "type"],
    },
    Log: {
        type: "object",
        properties: {id: {type: "string"}, type: {const: "Log"}, message: {type: "string"}},
        required: ["id", "type"],
    },
}

async function mountEditor(parentPath: string, modelValue: string) {
    const wrapper = i18nMount(TaskEditor, {
        props: {modelValue},
        global: {
            plugins: [createPinia()],
            stubs: {
                PluginSelect: {template: "<div data-test='plugin-select' />"},
                KsEditor: {template: "<textarea />"},
            },
            provide: {
                [FULL_SCHEMA_INJECTION_KEY as symbol]: computed(() => ({definitions, $ref: "#/definitions/Suite"})),
                [SCHEMA_DEFINITIONS_INJECTION_KEY as symbol]: computed(() => definitions),
                [BLOCK_SCHEMA_PATH_INJECTION_KEY as symbol]: computed(() => `#/definitions/Suite/properties/${parentPath}/items`),
                [PARENT_PATH_INJECTION_KEY as symbol]: parentPath,
            },
        },
    })
    await flushPromises()
    return wrapper
}

describe("TaskEditor type field", () => {
    test("shows type as a plain field when the block is not a discriminated union", async () => {
        const wrapper = await mountEditor("testCases", "id: first\n")

        expect(wrapper.find("[data-test='plugin-select']").exists()).toBe(false)
        expect(wrapper.find("[data-test='field-type']").exists()).toBe(true)
    })

    test("leaves type to the plugin select when the block is a discriminated union", async () => {
        const wrapper = await mountEditor("tasks", "id: hello\ntype: Log\n")

        expect(wrapper.find("[data-test='plugin-select']").exists()).toBe(true)
        expect(wrapper.find("[data-test='field-id']").exists()).toBe(true)
        expect(wrapper.find("[data-test='field-type']").exists()).toBe(false)
    })
})
