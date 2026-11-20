import {describe, it, expect, vi, beforeEach} from "vitest"
import {computed, defineComponent, ref, type ComputedRef} from "vue"
import {mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}}),
}))
vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: undefined}),
}))

import {useFlowStore} from "../../../stores/flow"
import {usePluginsStore, type JsonSchemaDef} from "../../../stores/plugins"
import {SECTIONS_IDS, useFlowFields} from "./useFlowFields"

// Seeds the real plugins store with a small fake flow root schema. Once `schemaType.flow` is set,
// `lazyLoadSchemaType` (called on mount) returns it as is, so no request is sent.
function setRootSchema(properties: Record<string, JsonSchemaDef>, required: string[] = ["id", "namespace"]) {
    usePluginsStore().schemaType = {
        flow: {
            $ref: "#/definitions/Flow",
            definitions: {Flow: {properties, required}},
        },
    }
}

function setup(flowSource: ComputedRef<string>) {
    let result!: ReturnType<typeof useFlowFields>
    const wrapper = mount(defineComponent({
        setup() {
            result = useFlowFields(flowSource)
            return () => null
        },
    }))
    return {result, wrapper}
}

describe("useFlowFields", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        setRootSchema({})
    })

    it("parsedFlow parses the source, and returns an empty object for malformed YAML rather than throwing", () => {
        const validSource = ref("id: test-flow\nnamespace: test.ns\ndescription: Test description")
        const {result: valid} = setup(computed(() => validSource.value))
        expect(valid.parsedFlow.value).toEqual({
            id: "test-flow",
            namespace: "test.ns",
            description: "Test description",
        })

        const consoleError = vi.spyOn(console, "error").mockImplementation(() => {})
        const malformedSource = ref("id: [unclosed bracket\n  - invalid: {")
        const {result: malformed} = setup(computed(() => malformedSource.value))
        expect(malformed.parsedFlow.value).toEqual({})
        expect(consoleError).toHaveBeenCalledWith("Error parsing flow YAML", expect.anything())
        consoleError.mockRestore()
    })

    it("fieldsFromSchemaTop is exactly the four main keys, in order", () => {
        const source = ref("id: test-flow\nnamespace: test.ns")
        const {result} = setup(computed(() => source.value))
        const topKeys = result.fieldsFromSchemaTop.value.map((field) => field.fieldKey)
        expect(topKeys).toEqual(["id", "namespace", "description", "inputs"])
    })

    it("fieldsFromSchemaRest excludes the main keys", () => {
        setRootSchema({
            id: {type: "string"},
            namespace: {type: "string"},
            description: {type: "string"},
            inputs: {type: "array"},
            tasks: {type: "array"},
            labels: {type: "object"},
        })

        const source = ref("id: test-flow\nnamespace: test.ns")
        const {result} = setup(computed(() => source.value))
        const restKeys = result.fieldsFromSchemaRest.value.map((field) => field.fieldKey)
        expect(restKeys).not.toContain("id")
        expect(restKeys).not.toContain("namespace")
        expect(restKeys).not.toContain("description")
        expect(restKeys).not.toContain("inputs")
        expect(restKeys).toEqual(expect.arrayContaining(["tasks", "labels"]))
    })

    it("the known section ids come back in SECTIONS_IDS order", () => {
        setRootSchema({
            outputs: {type: "array"},
            errors: {type: "array"},
            tasks: {type: "array"},
            afterExecution: {type: "array"},
            finally: {type: "array"},
            triggers: {type: "array"},
        })

        const source = ref("id: test-flow\nnamespace: test.ns")
        const {result} = setup(computed(() => source.value))
        const sectionKeys = result.fieldsFromSchemaRest.value.map((field) => field.fieldKey)
        expect(sectionKeys).toEqual(SECTIONS_IDS)
    })

    it("the known section ids come before every other field", () => {
        setRootSchema({
            labels: {type: "object"},
            outputs: {type: "array"},
            disabled: {type: "boolean"},
            tasks: {type: "array"},
            concurrency: {type: "object"},
            triggers: {type: "array"},
        })

        const source = ref("id: test-flow\nnamespace: test.ns")
        const {result} = setup(computed(() => source.value))
        const keys = result.fieldsFromSchemaRest.value.map((field) => field.fieldKey)

        expect(keys.slice(0, 3)).toEqual(["tasks", "triggers", "outputs"])
        expect(keys.slice(3)).toHaveLength(3)
        expect(keys.slice(3)).toEqual(expect.arrayContaining(["labels", "disabled", "concurrency"]))
    })

    it("a deprecated property is excluded, unless the parsed flow sets it", () => {
        setRootSchema({
            tasks: {type: "array"},
            oldDeprecated: {type: "string", $deprecated: true},
        })

        const unsetSource = ref("id: test-flow\nnamespace: test.ns")
        const {result: unsetResult} = setup(computed(() => unsetSource.value))
        const unsetKeys = unsetResult.fieldsFromSchemaRest.value.map((field) => field.fieldKey)
        expect(unsetKeys).not.toContain("oldDeprecated")

        const setSource = ref("id: test-flow\nnamespace: test.ns\noldDeprecated: legacy-value")
        const {result: setResult} = setup(computed(() => setSource.value))
        const setKeys = setResult.fieldsFromSchemaRest.value.map((field) => field.fieldKey)
        expect(setKeys).toContain("oldDeprecated")
    })

    it("id and namespace are disabled unless the flow is being created", () => {
        const flowStore = useFlowStore()
        flowStore.isCreating = false

        const source = ref("id: test-flow\nnamespace: test.ns\ndescription: Some flow")
        const {result: existingFlow} = setup(computed(() => source.value))

        const topFieldsExisting = existingFlow.fieldsFromSchemaTop.value
        const idFieldExisting = topFieldsExisting.find((field) => field.fieldKey === "id")
        const nsFieldExisting = topFieldsExisting.find((field) => field.fieldKey === "namespace")
        const descFieldExisting = topFieldsExisting.find((field) => field.fieldKey === "description")

        expect(idFieldExisting?.disabled).toBe(true)
        expect(nsFieldExisting?.disabled).toBe(true)
        expect(descFieldExisting?.disabled).toBe(false)

        flowStore.isCreating = true
        const {result: newFlow} = setup(computed(() => source.value))

        const topFieldsNew = newFlow.fieldsFromSchemaTop.value
        const idFieldNew = topFieldsNew.find((field) => field.fieldKey === "id")
        const nsFieldNew = topFieldsNew.find((field) => field.fieldKey === "namespace")
        const descFieldNew = topFieldsNew.find((field) => field.fieldKey === "description")

        expect(idFieldNew?.disabled).toBe(false)
        expect(nsFieldNew?.disabled).toBe(false)
        expect(descFieldNew?.disabled).toBe(false)
    })

    it("each field carries its schema from the root properties, falling back to an empty object", () => {
        const idSchema = {type: "string", pattern: "^[a-zA-Z0-9][a-zA-Z0-9_-]*"}
        const taskSchema = {type: "array", description: "Tasks in the flow"}
        setRootSchema({
            id: idSchema,
            tasks: taskSchema,
            labels: {type: "object"},
        })

        const source = ref("id: test-flow\nnamespace: test.ns")
        const {result} = setup(computed(() => source.value))

        const topFields = result.fieldsFromSchemaTop.value
        const idField = topFields.find((field) => field.fieldKey === "id")
        const descField = topFields.find((field) => field.fieldKey === "description")

        expect(idField?.schema).toEqual(idSchema)
        expect(idField?.required).toEqual(["id", "namespace"])
        expect(descField?.schema).toEqual({})

        const restFields = result.fieldsFromSchemaRest.value
        const tasksField = restFields.find((field) => field.fieldKey === "tasks")
        expect(tasksField?.schema).toEqual(taskSchema)
    })
})
