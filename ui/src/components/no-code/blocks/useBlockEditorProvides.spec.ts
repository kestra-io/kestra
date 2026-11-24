import {describe, it, expect, vi, beforeEach} from "vitest"
import {computed, defineComponent, h, inject, unref, type InjectionKey} from "vue"
import {mount} from "@vue/test-utils"

// --- mocks (hoisted) ---
// The real stores import `@kestra-io/kestra-sdk`, so they are replaced with plain objects. The
// composable only reads a few fields, which each test seeds before mounting.
const {flowStore, pluginsStore, defaultNamespace} = vi.hoisted(() => ({
    flowStore: {} as Record<string, unknown>,
    pluginsStore: {} as Record<string, unknown>,
    // Middle link of the namespace fallback chain, so it must be controllable.
    defaultNamespace: vi.fn<() => string | undefined>(),
}))
vi.mock("../../../stores/flow", () => ({useFlowStore: () => flowStore}))
vi.mock("../../../stores/plugins", () => ({usePluginsStore: () => pluginsStore}))
vi.mock("../../../composables/useNamespaces", () => ({defaultNamespace}))

import type {NoCodeProps} from "../../flows/noCodeTypes"
import {useBlockEditorProvides, type BlockEditorProvideContext} from "./useBlockEditorProvides"
import {
    BLOCK_SCHEMA_PATH_INJECTION_KEY,
    BLOCK_VALIDATION_ISSUES_INJECTION_KEY,
    CLOSE_TASK_FUNCTION_INJECTION_KEY,
    CREATE_TASK_FUNCTION_INJECTION_KEY,
    CREATING_FLOW_INJECTION_KEY,
    CREATING_TASK_INJECTION_KEY,
    DEFAULT_NAMESPACE_INJECTION_KEY,
    EDIT_TASK_FUNCTION_INJECTION_KEY,
    EDITING_TASK_INJECTION_KEY,
    FIELDNAME_INJECTION_KEY,
    FULL_SCHEMA_INJECTION_KEY,
    FULL_SOURCE_INJECTION_KEY,
    PARENT_PATH_INJECTION_KEY,
    POSITION_INJECTION_KEY,
    REF_PATH_INJECTION_KEY,
    ROOT_SCHEMA_INJECTION_KEY,
    SAVE_FLOW_FUNCTION_INJECTION_KEY,
    SCHEMA_DEFINITIONS_INJECTION_KEY,
    SCHEMA_LOADER_INJECTION_KEY,
    UPDATE_YAML_FUNCTION_INJECTION_KEY,
} from "../injectionKeys"

// Sentinel default: a key that is provided (even as `undefined`) never yields it.
const NOT_PROVIDED = Symbol("not-provided")

const KEYS = {
    FULL_SOURCE: FULL_SOURCE_INJECTION_KEY,
    BLOCK_VALIDATION_ISSUES: BLOCK_VALIDATION_ISSUES_INJECTION_KEY,
    PARENT_PATH: PARENT_PATH_INJECTION_KEY,
    REF_PATH: REF_PATH_INJECTION_KEY,
    POSITION: POSITION_INJECTION_KEY,
    CREATING_FLOW: CREATING_FLOW_INJECTION_KEY,
    DEFAULT_NAMESPACE: DEFAULT_NAMESPACE_INJECTION_KEY,
    CREATING_TASK: CREATING_TASK_INJECTION_KEY,
    EDITING_TASK: EDITING_TASK_INJECTION_KEY,
    FIELDNAME: FIELDNAME_INJECTION_KEY,
    BLOCK_SCHEMA_PATH: BLOCK_SCHEMA_PATH_INJECTION_KEY,
    FULL_SCHEMA: FULL_SCHEMA_INJECTION_KEY,
    ROOT_SCHEMA: ROOT_SCHEMA_INJECTION_KEY,
    SCHEMA_DEFINITIONS: SCHEMA_DEFINITIONS_INJECTION_KEY,
    SCHEMA_LOADER: SCHEMA_LOADER_INJECTION_KEY,
    CREATE_TASK_FUNCTION: CREATE_TASK_FUNCTION_INJECTION_KEY,
    EDIT_TASK_FUNCTION: EDIT_TASK_FUNCTION_INJECTION_KEY,
    CLOSE_TASK_FUNCTION: CLOSE_TASK_FUNCTION_INJECTION_KEY,
    UPDATE_YAML_FUNCTION: UPDATE_YAML_FUNCTION_INJECTION_KEY,
    SAVE_FLOW_FUNCTION: SAVE_FLOW_FUNCTION_INJECTION_KEY,
} as const

type KeyName = keyof typeof KEYS

// The injected values are opaque by default; the five provided functions are typed so tests can call them.
type Injected = Record<KeyName, unknown> & {
    CREATE_TASK_FUNCTION: BlockEditorProvideContext["createTask"]
    EDIT_TASK_FUNCTION: BlockEditorProvideContext["editTask"]
    CLOSE_TASK_FUNCTION: BlockEditorProvideContext["closeTask"]
    UPDATE_YAML_FUNCTION: BlockEditorProvideContext["updateYaml"]
    SAVE_FLOW_FUNCTION: BlockEditorProvideContext["saveFlow"]
}

function makeCtx(props: Partial<NoCodeProps> = {}) {
    return {
        props: props as NoCodeProps,
        flowYaml: computed(() => "id: my-flow"),
        validationIssuesByTask: computed(() => new Map<string, string[]>()),
        createTask: vi.fn(),
        editTask: vi.fn(),
        closeTask: vi.fn(),
        updateYaml: vi.fn(),
        saveFlow: vi.fn(),
    } satisfies BlockEditorProvideContext
}

/**
 * Mounts a host that calls the composable and a child that injects every key back, mirroring how
 * nested block fields consume them. An optional `seed` runs before the composable so a test can
 * set store state. Returns the raw injected values (refs are not unwrapped).
 */
function mountHost(props: Partial<NoCodeProps>, seed?: () => void) {
    const ctx = makeCtx(props)
    const collected: Record<string, unknown> = {}

    if (seed) {
        seed()
    }

    const Child = defineComponent({
        setup() {
            for (const [name, key] of Object.entries(KEYS)) {
                collected[name] = inject(key as unknown as InjectionKey<unknown>, NOT_PROVIDED)
            }
            return () => h("div")
        },
    })
    const Host = defineComponent({
        setup() {
            useBlockEditorProvides(ctx)
            return () => h(Child)
        },
    })

    mount(Host)

    const injected = collected as Injected
    const value = (name: KeyName) => unref(injected[name])
    return {ctx, injected, value}
}

function setup(props: Partial<NoCodeProps> = {}) {
    return mountHost(props)
}

/** Same as `setup`, but lets a test seed the stores before the composable runs. */
function setupWithStores(seed: () => void, props: Partial<NoCodeProps> = {}) {
    return mountHost(props, seed)
}

describe("useBlockEditorProvides", () => {
    beforeEach(() => {
        vi.clearAllMocks()
        defaultNamespace.mockReturnValue(undefined)
        // Reset the mocked stores so state never leaks between tests.
        for (const store of [flowStore, pluginsStore]) {
            Object.keys(store).forEach((k) => delete store[k])
        }
    })

    it.each(Object.keys(KEYS) as KeyName[])("provides the %s key", (name) => {
        const {injected} = setup()
        expect(injected[name]).not.toBe(NOT_PROVIDED)
    })

    it("provides the context's own flowYaml and validation issues as-is", () => {
        const {ctx, injected} = setup()
        expect(injected.FULL_SOURCE).toBe(ctx.flowYaml)
        expect(injected.BLOCK_VALIDATION_ISSUES).toBe(ctx.validationIssuesByTask)
    })

    describe("prop defaults", () => {
        it("defaults PARENT_PATH to an empty string and POSITION to 'after' when props omit them", () => {
            const {value} = setup()
            expect(value("PARENT_PATH")).toBe("")
            expect(value("POSITION")).toBe("after")
        })

        it("uses the props when they are given", () => {
            const {value} = setup({parentPath: "tasks[0]", position: "before", refPath: 2, fieldName: "tasks"})
            expect(value("PARENT_PATH")).toBe("tasks[0]")
            expect(value("POSITION")).toBe("before")
            expect(value("REF_PATH")).toBe(2)
            expect(value("FIELDNAME")).toBe("tasks")
        })

        it("defaults CREATING_TASK and EDITING_TASK to false", () => {
            const {value} = setup()
            expect(value("CREATING_TASK")).toBe(false)
            expect(value("EDITING_TASK")).toBe(false)
        })

        it("passes CREATING_TASK and EDITING_TASK through when set", () => {
            const {value} = setup({creatingTask: true, editingTask: true})
            expect(value("CREATING_TASK")).toBe(true)
            expect(value("EDITING_TASK")).toBe(true)
        })

        it("leaves REF_PATH and FIELDNAME undefined when props omit them", () => {
            const {value} = setup()
            expect(value("REF_PATH")).toBeUndefined()
            expect(value("FIELDNAME")).toBeUndefined()
        })
    })

    describe("CREATING_FLOW", () => {
        it("defaults to false", () => {
            const {value} = setupWithStores(() => {
                flowStore.isCreating = undefined
            })
            expect(value("CREATING_FLOW")).toBe(false)
        })

        it("mirrors the flow store when it is creating", () => {
            const {value} = setupWithStores(() => {
                flowStore.isCreating = true
            })
            expect(value("CREATING_FLOW")).toBe(true)
        })
    })

    describe("DEFAULT_NAMESPACE", () => {
        it("prefers the flow's own namespace", () => {
            defaultNamespace.mockReturnValue("from.default")
            const {value} = setupWithStores(() => {
                flowStore.flow = {namespace: "flow.own"}
            })
            expect(value("DEFAULT_NAMESPACE")).toBe("flow.own")
        })

        it("falls back to defaultNamespace() when the flow has no namespace", () => {
            defaultNamespace.mockReturnValue("from.default")
            const {value} = setupWithStores(() => {
                flowStore.flow = undefined
            })
            expect(value("DEFAULT_NAMESPACE")).toBe("from.default")
        })

        it("falls back to company.team when neither is available", () => {
            defaultNamespace.mockReturnValue(undefined)
            const {value} = setupWithStores(() => {
                flowStore.flow = undefined
            })
            expect(value("DEFAULT_NAMESPACE")).toBe("company.team")
        })
    })

    describe("schemas", () => {
        it("BLOCK_SCHEMA_PATH prefers the prop", () => {
            const {value} = setupWithStores(() => {
                pluginsStore.flowSchema = {$ref: "#/definitions/Flow"}
            }, {blockSchemaPath: "#/definitions/io.kestra.plugin.Task"})
            expect(value("BLOCK_SCHEMA_PATH")).toBe("#/definitions/io.kestra.plugin.Task")
        })

        it("BLOCK_SCHEMA_PATH falls back to the flow schema $ref", () => {
            const {value} = setupWithStores(() => {
                pluginsStore.flowSchema = {$ref: "#/definitions/Flow"}
            })
            expect(value("BLOCK_SCHEMA_PATH")).toBe("#/definitions/Flow")
        })

        it("BLOCK_SCHEMA_PATH falls back to an empty string when there is neither", () => {
            const {value} = setupWithStores(() => {
                pluginsStore.flowSchema = undefined
            })
            expect(value("BLOCK_SCHEMA_PATH")).toBe("")
        })

        it("provides the store's schema, root schema and definitions when present", () => {
            const flowSchema = {$ref: "#/definitions/Flow"}
            const flowRootSchema = {type: "object"}
            const flowDefinitions = {Flow: {type: "object"}}
            const {value} = setupWithStores(() => {
                pluginsStore.flowSchema = flowSchema
                pluginsStore.flowRootSchema = flowRootSchema
                pluginsStore.flowDefinitions = flowDefinitions
            })
            expect(value("FULL_SCHEMA")).toEqual(flowSchema)
            expect(value("ROOT_SCHEMA")).toEqual(flowRootSchema)
            expect(value("SCHEMA_DEFINITIONS")).toEqual(flowDefinitions)
        })

        // Topology also mounts read-only on an execution, where nothing ever needs the megabytes of schema.
        it("hands forms the flow schema loader without loading it", () => {
            const flowSchemaLoader = {load: vi.fn()}
            const {injected} = setupWithStores(() => {
                pluginsStore.flowSchemaLoader = flowSchemaLoader
            })

            expect(injected.SCHEMA_LOADER).toBe(flowSchemaLoader)
            expect(flowSchemaLoader.load).not.toHaveBeenCalled()
        })

        it("falls back to an empty object for the schema, root schema and definitions", () => {
            const {value} = setupWithStores(() => {
                pluginsStore.flowSchema = undefined
                pluginsStore.flowRootSchema = undefined
                pluginsStore.flowDefinitions = undefined
            })
            expect(value("FULL_SCHEMA")).toEqual({})
            expect(value("ROOT_SCHEMA")).toEqual({})
            expect(value("SCHEMA_DEFINITIONS")).toEqual({})
        })
    })

    describe("provided functions forward to the context callbacks", () => {
        it("createTask forwards all four arguments", () => {
            const {ctx, injected} = setup()
            const anchor = document.createElement("button")
            injected.CREATE_TASK_FUNCTION("tasks", "#/definitions/Task", 3, anchor)
            expect(ctx.createTask).toHaveBeenCalledTimes(1)
            expect(ctx.createTask).toHaveBeenCalledWith("tasks", "#/definitions/Task", 3, anchor)
        })

        it("createTask forwards an omitted anchor element as undefined", () => {
            const {ctx, injected} = setup()
            injected.CREATE_TASK_FUNCTION("tasks", "#/definitions/Task", undefined)
            expect(ctx.createTask).toHaveBeenCalledWith("tasks", "#/definitions/Task", undefined, undefined)
        })

        it("editTask forwards all four arguments", () => {
            const {ctx, injected} = setup()
            injected.EDIT_TASK_FUNCTION("tasks", "#/definitions/Task", 1, true)
            expect(ctx.editTask).toHaveBeenCalledTimes(1)
            expect(ctx.editTask).toHaveBeenCalledWith("tasks", "#/definitions/Task", 1, true)
        })

        it("closeTask calls the callback without arguments", () => {
            const {ctx, injected} = setup()
            injected.CLOSE_TASK_FUNCTION()
            expect(ctx.closeTask).toHaveBeenCalledTimes(1)
            expect(ctx.closeTask).toHaveBeenCalledWith()
        })

        it("updateYaml forwards the yaml", () => {
            const {ctx, injected} = setup()
            injected.UPDATE_YAML_FUNCTION("id: my-flow\nnamespace: company.team")
            expect(ctx.updateYaml).toHaveBeenCalledTimes(1)
            expect(ctx.updateYaml).toHaveBeenCalledWith("id: my-flow\nnamespace: company.team")
        })

        it("saveFlow calls the callback", () => {
            const {ctx, injected} = setup()
            injected.SAVE_FLOW_FUNCTION()
            expect(ctx.saveFlow).toHaveBeenCalledTimes(1)
        })
    })
})
