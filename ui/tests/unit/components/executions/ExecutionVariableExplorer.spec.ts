import {beforeEach, describe, expect, test, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"

import ExecutionVariableExplorer from "../../../../src/components/executions/outputs/ExecutionVariableExplorer.vue"
import {useExecutionsStore} from "../../../../src/stores/executions"

vi.mock("@kestra-io/kestra-sdk/outputs", () => ({
    taskOutputsInformation: vi.fn().mockResolvedValue([]),
    taskRunOutputs: vi.fn().mockResolvedValue({}),
}))

vi.mock("../../../../src/components/executions/FilePreview.vue", () => ({
    default: {
        name: "FilePreview",
        props: ["path", "executionId"],
        template: "<div />",
    },
}))

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {
        en: {
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
        },
    },
})

const globalConfig = {
    plugins: [i18n],
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

function mountExplorer(variables: Record<string, unknown>) {
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
        inputs: {},
        taskRunList: [],
        state: {
            current: "SUCCESS",
            histories: [],
            startDate: "2026-01-01T00:00:00Z",
            duration: "PT1S",
            getStartDate: "2026-01-01T00:00:00Z",
            getEndDate: "",
            getDuration: "PT1S",
        },
    }

    return mount(ExecutionVariableExplorer, {global: globalConfig})
}

async function selectVariable(wrapper: ReturnType<typeof mount>, variableName: string) {
    const sidebar = wrapper.findComponent({name: "SidebarList"})
    const variableItem = (sidebar.props("sections") as any[])
        .find((section) => section.key === "variables")
        .items
        .find((item: {label: string}) => item.label === variableName)

    await sidebar.vm.$emit("select", variableItem)
    await flushPromises()
}

describe("ExecutionVariableExplorer", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
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
})
