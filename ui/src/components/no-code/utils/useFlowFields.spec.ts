import {describe, it, expect, vi, beforeEach} from "vitest"
import {computed, defineComponent, ref, type ComputedRef} from "vue"
import {mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"

vi.mock("../../../stores/flow", async () => {
    const pinia = await import("pinia")
    const vue = await import("vue")
    return {
        useFlowStore: pinia.defineStore("flow", () => {
            const isCreating = vue.ref(false)
            return {isCreating}
        }),
    }
})

vi.mock("../../../stores/plugins", async () => {
    const pinia = await import("pinia")
    const vue = await import("vue")
    const vitest = await import("vitest")
    return {
        usePluginsStore: pinia.defineStore("plugins", () => {
            const lazyLoadSchemaType = vitest.vi.fn()
            const flowRootSchema = vue.ref<{required?: string[]}>({required: ["id", "namespace"]})
            const flowRootProperties = vue.ref<Record<string, Record<string, unknown>>>({})
            return {
                lazyLoadSchemaType,
                flowRootSchema,
                flowRootProperties,
            }
        }),
    }
})

import {useFlowStore} from "../../../stores/flow"
import {usePluginsStore} from "../../../stores/plugins"
import {SECTIONS_IDS, useFlowFields} from "./useFlowFields"

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
        const pluginsStore = usePluginsStore()
        pluginsStore.flowRootProperties = {
            id: {type: "string"},
            namespace: {type: "string"},
            description: {type: "string"},
            inputs: {type: "array"},
            tasks: {type: "array"},
            labels: {type: "object"},
        }

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
        const pluginsStore = usePluginsStore()
        pluginsStore.flowRootProperties = {
            outputs: {type: "array"},
            errors: {type: "array"},
            tasks: {type: "array"},
            afterExecution: {type: "array"},
            finally: {type: "array"},
            triggers: {type: "array"},
        }

        const source = ref("id: test-flow\nnamespace: test.ns")
        const {result} = setup(computed(() => source.value))
        const sectionKeys = result.fieldsFromSchemaRest.value.map((field) => field.fieldKey)
        expect(sectionKeys).toEqual(SECTIONS_IDS)
    })

    it("a deprecated property is excluded, unless the parsed flow sets it", () => {
        const pluginsStore = usePluginsStore()
        pluginsStore.flowRootProperties = {
            tasks: {type: "array"},
            oldDeprecated: {type: "string", $deprecated: true},
        }

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
        const pluginsStore = usePluginsStore()
        const taskSchema = {type: "array", description: "Tasks in the flow"}
        pluginsStore.flowRootProperties = {
            tasks: taskSchema,
            labels: {type: "object"},
        }

        const source = ref("id: test-flow\nnamespace: test.ns")
        const {result} = setup(computed(() => source.value))

        const topFields = result.fieldsFromSchemaTop.value
        const idField = topFields.find((field) => field.fieldKey === "id")
        const descField = topFields.find((field) => field.fieldKey === "description")

        expect(idField?.schema).toEqual({})
        expect(descField?.schema).toEqual({})

        const restFields = result.fieldsFromSchemaRest.value
        const tasksField = restFields.find((field) => field.fieldKey === "tasks")
        expect(tasksField?.schema).toEqual(taskSchema)
    })
})
