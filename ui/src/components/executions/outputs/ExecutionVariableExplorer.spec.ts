import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../../tests/unit/i18nMount"

import {createPinia, setActivePinia} from "pinia"
import ExecutionVariableExplorer from "./ExecutionVariableExplorer.vue"
import type {ExplorerItem, ExplorerSection} from "./SidebarList.vue"
import {useExecutionsStore, type Execution} from "../../../stores/executions"
import type {FlowForExecution} from "@kestra-io/kestra-sdk"
import * as OutputsAPI from "@kestra-io/kestra-sdk/outputs"
import * as Utils from "../../../utils/utils"
vi.mock("vue-router", async (importOriginal) => ({
    ...(await importOriginal<typeof import("vue-router")>()),
    useRoute: () => ({query: {}}),
    useRouter: () => ({push: vi.fn()}),
}))

vi.mock("@kestra-io/kestra-sdk/outputs", () => ({
    taskOutputsInformation: vi.fn().mockResolvedValue([]),
    taskRunOutputs: vi.fn().mockResolvedValue({}),
    executionOutputs: vi.fn().mockResolvedValue({}),
}))

vi.mock("../FilePreview.vue", () => ({
    default: {
        name: "FilePreview",
        props: ["path", "executionId"],
        template: "<div />",
    },
}))

const messages = {
    collapse: "Collapse",
    expand: "Expand",
    variables: "Variables",
    triggers: "Triggers",
    inputs: "Inputs",
    flow_outputs: "Outputs",
    variable_explorer: {
        empty: "No variables",
        n_items: "{count} items",
        n_keys: "{count} keys",
        one_item: "1 item",
        one_key: "1 key",
        raw_json: "Raw JSON",
        search_placeholder: "Search",
        select_prompt: "Select a variable",
        tasks_outputs: "Task outputs",
        tree: "Tree",
    },
    loop_iterations_notice: {
        outputs: "Loop iterations outputs live in their sub-executions",
        view_iterations: "View iterations",
    },
}

const globalConfig = {
    stubs: {
        KsCollapse: {template: "<div><slot /></div>"},
        KsCollapseItem: {template: "<div><slot name=\"title\" /><slot /></div>"},
        KsEditor: true,
        KsIconButton: {template: "<button><slot /></button>"},
        KsNoData: true,
        KsScrollbar: {template: "<div><slot /></div>"},
        KsSearch: true,
        KsSegmented: true,
        KsSplitter: {template: "<div><slot /></div>"},
        KsSplitterPanel: {template: "<div><slot /></div>"},
        KsTag: {template: "<span><slot /></span>"},
        ExpressionDebugger: true,
    },
}

function computeFlowForExecution(flow: Partial<FlowForExecution> = {}): FlowForExecution {
    return {
        id: "flow",
        namespace: "io.kestra.tests",
        disabled: false,
        deleted: false,
        draft: false,
        tasks: [],
        ...flow,
    }
}

function mountExplorer(
    variables: Record<string, unknown>,
    trigger?: {id: string; type: string; variables?: Record<string, unknown>},
    taskRunList: Execution["taskRunList"] = [],
) {
    const executionsStore = useExecutionsStore()
    executionsStore.execution = {
        id: "execution-id",
        originalId: "execution-id",
        namespace: "io.kestra.tests",
        flowId: "flow",
        flowRevision: 1,
        metadata: {
            originalCreatedDate: "2026-01-01T00:00:00Z",
            attemptNumber: 1,
        },
        variables,
        trigger,
        inputs: {},
        taskRunList,
        state: {
            current: "SUCCESS",
            histories: [],
            startDate: "2026-01-01T00:00:00Z",
            duration: "PT1S",
        },
    }

    return i18nMount(ExecutionVariableExplorer, {messages, global: globalConfig})
}

function sidebarSections(wrapper: ReturnType<typeof i18nMount>): ExplorerSection[] {
    return wrapper.findComponent({name: "SidebarList"}).props("sections") as ExplorerSection[]
}

async function selectVariable(wrapper: ReturnType<typeof i18nMount>, itemName: string, sectionKey = "variables") {
    const sidebar = wrapper.findComponent({name: "SidebarList"})
    const item = sidebarSections(wrapper)
        .find((section) => section.key === sectionKey)
        ?.items
        .find((candidate) => candidate.label === itemName)

    await sidebar.vm.$emit("select", item)
    await flushPromises()
}

async function mountLoopExplorer(outputsByTaskRunId: Record<string, Record<string, unknown>>) {
    vi.mocked(OutputsAPI.taskOutputsInformation).mockResolvedValueOnce([
        {taskRunId: "loop-run", taskId: "loop"},
        {taskRunId: "log-run", taskId: "log"},
    ])
    vi.mocked(OutputsAPI.taskRunOutputs).mockImplementation(async ({taskRunId}) => outputsByTaskRunId[taskRunId] ?? {})

    useExecutionsStore().flow = {
        id: "flow",
        namespace: "io.kestra.tests",
        tasks: [
            {id: "loop", type: "io.kestra.plugin.core.flow.Loop", tasks: [{id: "hello", type: "io.kestra.plugin.core.log.Log"}]},
            {id: "log", type: "io.kestra.plugin.core.log.Log"},
        ],
    } as FlowForExecution

    const wrapper = mountExplorer({}, undefined, [
        {id: "loop-run", taskId: "loop", state: {current: "SUCCESS", histories: []}},
        {id: "log-run", taskId: "log", state: {current: "SUCCESS", histories: []}},
    ])
    await flushPromises()

    return wrapper
}

describe("ExecutionVariableExplorer", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    test("tags the flow outputs declared searchable by the flow", async () => {
        vi.mocked(OutputsAPI.executionOutputs).mockResolvedValueOnce({orderIds: "ORD-1", plain: "x"})
        useExecutionsStore().flow = computeFlowForExecution({
            outputs: [
                {
                    id: "orderIds", 
                    type: "STRING", 
                    searchable: true,
                },
                {
                    id: "plain", 
                    type: "STRING", 
                },
            ],
        })
        
        const wrapper = mountExplorer({})
        await flushPromises()

        const items = sidebarSections(wrapper).find((section) => section.key === "flowOutputs")?.items
        await selectVariable(wrapper, "orderIds", "flowOutputs")

        expect(items?.map((item) => [item.label, item.searchable])).toEqual([["orderIds", true], ["plain", false]])
        expect(wrapper.find("[data-test=viewer-title] [data-test=searchable-tag]").exists()).toBe(true)

        await selectVariable(wrapper, "plain", "flowOutputs")

        expect(wrapper.find("[data-test=viewer-title] [data-test=searchable-tag]").exists()).toBe(false)
    })

    test("uses render for variables containing expressions", async () => {
        const wrapper = mountExplorer({
            greeting: "Hello {{ inputs.name }}",
        })

        await flushPromises()
        await selectVariable(wrapper, "greeting")

        expect(wrapper.findComponent({name: "ExpressionDebugger"}).props("expression"))
            .toBe("{{ render(vars.greeting) }}")
    })

    test("uses render for variables containing expressions", async () => {
        const wrapper = mountExplorer({
            greeting: "Hello {{ inputs.name }}",
        })

        await flushPromises()
        await selectVariable(wrapper, "greeting")

        expect(wrapper.findComponent({name: "ExpressionDebugger"}).props("expression"))
            .toBe("{{ render(vars.greeting) }}")
    })

    test("marks expression-valued variables in the sidebar", async () => {
        const wrapper = mountExplorer({
            greeting: "Hello {{ inputs.name }}",
            plain: "Hello world",
        })

        await flushPromises()

        const sidebar = wrapper.findComponent({name: "SidebarList"})
        const variables = sidebar.props("sections")
            .find((section: {key: string; items: {label: string}[]}) => section.key === "variables")
            .items

        expect(variables.find((item: {label: string; isExpression: boolean}) => item.label === "greeting").isExpression).toBe(true)
        expect(variables.find((item: {label: string; isExpression: boolean}) => item.label === "plain").isExpression).toBe(false)
    })

    test("previews a nested file selected from the tree", async () => {
        const fileUri = "kestra:///outputs/report.txt"
        const wrapper = mountExplorer({
            files: {
                report: {uri: fileUri},
                status: "ready",
            },
        })
        await flushPromises()

        await selectVariable(wrapper, "files")

        const reportRow = wrapper
            .findAll(".json-tree__row")
            .find((row) => row.text().includes("\"report\""))

        expect(reportRow).toBeDefined()

        await reportRow!.trigger("click")
        await flushPromises()

        const filePreview = wrapper.findComponent({name: "FilePreview"})
        expect(filePreview.exists()).toBe(true)
        expect(filePreview.props("path")).toBe(fileUri)
    })

    test("previews a single-key file wrapper immediately when it is auto-selected", async () => {
        const fileUri = "kestra:///outputs/result.txt"
        const wrapper = mountExplorer({
            myFile: {uri: fileUri},
        })
        await flushPromises()

        await selectVariable(wrapper, "myFile")

        const filePreview = wrapper.findComponent({name: "FilePreview"})
        expect(filePreview.exists()).toBe(true)
        expect(filePreview.props("path")).toBe(fileUri)
    })

    test("offers the lone file nested in a selection to the debugger without hiding the tree", async () => {
        const fileUri = "kestra:///outputs/products.json"
        const wrapper = mountExplorer({
            transform: {
                vars: {},
                exitCode: 0,
                outputFiles: {"products.json": fileUri},
            },
        })
        await flushPromises()

        await selectVariable(wrapper, "transform")

        const expressionDebugger = wrapper.findComponent({name: "ExpressionDebugger"})
        expect(expressionDebugger.props("fileUri")).toBe(fileUri)
        expect(expressionDebugger.props("expression")).toBe("{{ vars.transform }}")
        // the selection keeps showing the tree, the file is only offered on the side
        expect(wrapper.findComponent({name: "FilePreview"}).exists()).toBe(false)
        expect(wrapper.findAll(".json-tree__row").length).toBeGreaterThan(0)
    })

    test("does not re-root the tree when selecting intermediate object rows", async () => {
        const wrapper = mountExplorer({
            bundle: {
                values: {
                    a: {
                        b: "one",
                        c: "two",
                    },
                    nestedfile: "kestra:///outputs/report.txt",
                },
            },
        })
        await flushPromises()

        await selectVariable(wrapper, "bundle")

        const findRow = (text: string) =>
            wrapper.findAll(".json-tree__row").find((row) => row.text().includes(text))

        await findRow("\"a\"")!.trigger("click")
        await flushPromises()

        await findRow("\"b\"")!.trigger("click")
        await flushPromises()

        expect(wrapper.findComponent({name: "ExpressionDebugger"}).props("expression"))
            .toBe("{{ vars.bundle.values.a.b }}")
    })

    test("maps the Triggers section onto the run-context shape, not the raw ExecutionTrigger DTO", async () => {
        const wrapper = mountExplorer({}, {
            id: "every_minute",
            type: "io.kestra.plugin.core.trigger.Schedule",
            variables: {date: "2026-01-01T00:00:00Z"},
        })
        await flushPromises()

        const triggerItems = sidebarSections(wrapper)
            .find((section) => section.key === "triggers")
            ?.items ?? []

        // trigger variables sit at the top level, id/type only under `_context` — mirroring
        // RunVariables.java, not the DTO's own `id` / `type` / `variables` fields.
        expect(triggerItems.map((item) => item.label)).toEqual(["date", "_context"])

        await selectVariable(wrapper, "date", "triggers")
        expect(wrapper.findComponent({name: "ExpressionDebugger"}).props("expression"))
            .toBe("{{ trigger.date }}")

        // `_context` starts with an underscore — asserts the dot form (`trigger._context`),
        // not the bracket-subscript fallback (`trigger["_context"]`) that isValidVariable
        // used to force for any leading-underscore key.
        await selectVariable(wrapper, "_context", "triggers")
        expect(wrapper.findComponent({name: "ExpressionDebugger"}).props("expression"))
            .toBe("{{ trigger._context }}")
    })

    describe("loop iterations notice", () => {
        test("shows the notice, narrowed to the Loop task, once a Loop output reports iterations", async () => {
            const wrapper = await mountLoopExplorer({"loop-run": {iterationCount: 3}})

            await selectVariable(wrapper, "loop", "tasksOutputs")

            expect(wrapper.find("[data-test=loop-iterations-notice]").exists()).toBe(true)
            expect(wrapper.findComponent({name: "RouterLink"}).props("to").query["filters[taskId][EQUALS]"]).toBe("loop")
        })

        test("hides the notice for a Loop that ran no iteration", async () => {
            const wrapper = await mountLoopExplorer({"loop-run": {iterationCount: 0}})

            await selectVariable(wrapper, "loop", "tasksOutputs")

            expect(wrapper.find("[data-test=loop-iterations-notice]").exists()).toBe(false)
        })

        test("hides the notice for the output of a task that is not a Loop", async () => {
            const wrapper = await mountLoopExplorer({"log-run": {iterationCount: 3}})

            await selectVariable(wrapper, "log", "tasksOutputs")

            expect(wrapper.find("[data-test=loop-iterations-notice]").exists()).toBe(false)
        })
    })

    test("keeps an empty-string variable visible in the sidebar and scalar viewer", async () => {
        const wrapper = mountExplorer({
            empty: "",
        })
        await flushPromises()

        const items = sidebarSections(wrapper)
            .find((section) => section.key === "variables")
            ?.items ?? []

        const byLabel = Object.fromEntries(
            items.map((item): [string, ExplorerItem] => [item.label, item]),
        )
        expect(byLabel.empty.preview).toBe(Utils.EMPTY_STRING_DISPLAY)

        await selectVariable(wrapper, "empty")
        expect(wrapper.find(".viewer__scalar").text()).toBe(Utils.EMPTY_STRING_DISPLAY)
    })
})

describe("ExecutionVariableExplorer on a phone", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        vi.stubGlobal("matchMedia", (query: string) => ({
            matches: true,
            media: query,
            addEventListener: vi.fn(),
            removeEventListener: vi.fn(),
            addListener: vi.fn(),
            removeListener: vi.fn(),
        }))
    })

    afterEach(() => {
        vi.unstubAllGlobals()
    })

    const listIsVisible = (wrapper: ReturnType<typeof i18nMount>) =>
        wrapper.findComponent({name: "SidebarList"}).attributes("style") !== "display: none;"

    test("starts on the full-width list without the viewer", async () => {
        // Given / When
        const wrapper = mountExplorer({greeting: "Hello"})
        await flushPromises()

        // Then
        expect(listIsVisible(wrapper)).toBe(true)
        expect(wrapper.find("[data-test=\"variable-back\"]").exists()).toBe(false)
    })

    test("opens the viewer with a breadcrumb when a variable is tapped and returns with the back button", async () => {
        // Given
        const wrapper = mountExplorer({greeting: "Hello"})
        await flushPromises()

        // When
        await selectVariable(wrapper, "greeting")

        // Then
        expect(listIsVisible(wrapper)).toBe(false)
        expect(wrapper.find(".viewer__breadcrumb").text()).toBe("vars › greeting")

        // When
        await wrapper.find("[data-test=\"variable-back\"]").trigger("click")

        // Then
        expect(listIsVisible(wrapper)).toBe(true)
        expect(wrapper.find("[data-test=\"variable-back\"]").exists()).toBe(false)
    })

    test("keeps the debugger prefilled when a variable is selected while it is collapsed", async () => {
        // Given
        const wrapper = mountExplorer({greeting: "Hello"})
        await flushPromises()

        // When
        await selectVariable(wrapper, "greeting")

        // Then
        expect(wrapper.findComponent({name: "ExpressionDebugger"}).props("expression")).toBe("{{ vars.greeting }}")
    })
})
