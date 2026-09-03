import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {within, userEvent, expect, waitFor} from "storybook/test"
import {defineComponent, markRaw, ref} from "vue"
import {vueRouter} from "storybook-vue3-router"
import CodeTagsIcon from "vue-material-design-icons/CodeTags.vue"
import FileTreeOutlineIcon from "vue-material-design-icons/FileTreeOutline.vue"

import BlockEditor from "../../../../src/components/no-code/blocks/BlockEditor.vue"
import MultiPanelTabs from "../../../../src/components/MultiPanelTabs.vue"
import PluginDocumentation from "../../../../src/components/plugins/PluginDocumentation.vue"
import {useFlowStore} from "../../../../src/stores/flow"
import {usePlaygroundStore} from "../../../../src/stores/playground"
import {usePluginsStore} from "../../../../src/stores/plugins"
import {setMockClient} from "@kestra-io/kestra-sdk"
import type {Panel} from "../../../../src/utils/multiPanelTypes"
import {CICD_PIPELINE_YAML, mockNoCodeTransport, seedIfTaskSchema} from "./blockEditorFeedbackFixtures"
import {storageKeys, taskEditDefaultModes} from "../../../../src/utils/constants"

const meta: Meta = {
    title: "No-code/Feedback fixes",
    parameters: {
        layout: "fullscreen",
    },
    // These stories exercise the inline (dock) task-edit surface. The app now
    // defaults to opening tasks in a modal instead, so opening a task by click
    // would surface the modal rather than the inline panel these stories assert
    // on — pin the TAB (dock) mode for the whole file.
    decorators: [
        () => ({
            setup() {
                localStorage.setItem(storageKeys.TASK_EDIT_DEFAULT_MODE, taskEditDefaultModes.TAB)
            },
            template: "<story/>",
        }),
    ],
}

export default meta
type Story = StoryObj

// Switch with three real cases plus a defaults lane, routing a support ticket
// by priority.
const SWITCH_YAML = `id: route_ticket
namespace: company.support
tasks:
  - id: route_by_priority
    type: io.kestra.plugin.core.flow.Switch
    value: "{{ inputs.priority }}"
    cases:
      critical:
        - id: page_oncall
          type: io.kestra.plugin.core.http.Request
          uri: https://api.pagerduty.com/incidents
          method: POST
        - id: notify_critical_slack
          type: io.kestra.plugin.core.log.Log
          level: WARN
          message: "Critical ticket {{ inputs.ticketId }} paged on-call"
      high:
        - id: notify_high_priority
          type: io.kestra.plugin.core.log.Log
          level: WARN
          message: "High priority ticket {{ inputs.ticketId }} assigned"
      low:
        - id: queue_for_triage
          type: io.kestra.plugin.core.log.Log
          message: "Ticket {{ inputs.ticketId }} queued for triage"
    defaults:
      - id: log_unhandled_priority
        type: io.kestra.plugin.core.log.Log
        level: WARN
        message: "Unhandled priority {{ inputs.priority }} for ticket {{ inputs.ticketId }}"
`

// Sequential wrapping several real report-generation steps.
const SEQUENTIAL_YAML = `id: monthly_report
namespace: company.finance
tasks:
  - id: generate_report
    type: io.kestra.plugin.core.flow.Sequential
    tasks:
      - id: fetch_transactions
        type: io.kestra.plugin.core.http.Request
        uri: https://api.example.com/transactions?month={{ trigger.date | date('yyyy-MM') }}
        method: GET
      - id: build_spreadsheet
        type: io.kestra.plugin.scripts.python.Script
        script: |
          import pandas as pd
          df = pd.read_json("transactions.json")
          df.to_excel("monthly_report.xlsx")
      - id: email_finance_team
        type: io.kestra.plugin.core.http.Request
        uri: https://api.example.com/notifications/email
        method: POST
`

// ForEachItem iterating over a realistic list of regions to warm a cache in
// each one.
const FOREACH_ITEM_YAML = `id: warm_regional_caches
namespace: company.infra
tasks:
  - id: warm_caches
    type: io.kestra.plugin.core.flow.ForEachItem
    items: "{{ ['eu-west-1', 'us-east-1', 'ap-southeast-2'] }}"
    tasks:
      - id: warm_cache
        type: io.kestra.plugin.core.http.Request
        uri: "https://cache-warmer.example.com/regions/{{ taskrun.value }}/warm"
        method: POST
`

// A realistic ETL DAG with fan-out/fan-in: two independent extracts feed a
// merge step, which feeds both a data-quality check and a load step, and a
// final notification depends on the load. Several sub-tasks declare more
// than one dependsOn.
const DAG_YAML = `id: customer_360_etl
namespace: company.data
tasks:
  - id: customer_360
    type: io.kestra.plugin.core.flow.Dag
    tasks:
      - task:
          id: extract_crm
          type: io.kestra.plugin.core.http.Request
          uri: https://crm.example.com/api/customers/export
          method: GET
      - task:
          id: extract_billing
          type: io.kestra.plugin.core.http.Request
          uri: https://billing.example.com/api/invoices/export
          method: GET
      - task:
          id: merge_datasets
          type: io.kestra.plugin.scripts.python.Script
          script: |
            import pandas as pd
            crm = pd.read_json("crm.json")
            billing = pd.read_json("billing.json")
            crm.merge(billing, on="customer_id").to_json("merged.json")
        dependsOn:
          - extract_crm
          - extract_billing
      - task:
          id: data_quality_check
          type: io.kestra.plugin.scripts.python.Script
          script: |
            import pandas as pd
            df = pd.read_json("merged.json")
            assert df["customer_id"].notna().all(), "Missing customer_id"
        dependsOn:
          - merge_datasets
      - task:
          id: load_warehouse
          type: io.kestra.plugin.scripts.shell.Commands
          commands:
            - snowsql -f load_customer_360.sql
        dependsOn:
          - data_quality_check
      - task:
          id: notify_data_team
          type: io.kestra.plugin.core.log.Log
          message: customer_360 warehouse table refreshed
        dependsOn:
          - load_warehouse
`

// Deeply nested: an If whose "then" branch contains a Parallel, one of whose
// branches is itself an If — proving recursive rendering, not just one level.
const DEEPLY_NESTED_YAML = `id: incident_response
namespace: company.sre
tasks:
  - id: check_severity
    type: io.kestra.plugin.core.flow.If
    condition: "{{ inputs.severity >= 3 }}"
    then:
      - id: mitigate_in_parallel
        type: io.kestra.plugin.core.flow.Parallel
        tasks:
          - id: scale_up_service
            type: io.kestra.plugin.scripts.shell.Commands
            commands:
              - kubectl scale deployment/api --replicas=10
          - id: check_customer_impact
            type: io.kestra.plugin.core.flow.If
            condition: "{{ inputs.affectedCustomers > 1000 }}"
            then:
              - id: notify_customer_success
                type: io.kestra.plugin.core.log.Log
                level: WARN
                message: Large-impact incident — notifying customer success team
            else:
              - id: log_minor_impact
                type: io.kestra.plugin.core.log.Log
                message: Impact contained, no customer notification needed
    else:
      - id: log_low_severity
        type: io.kestra.plugin.core.log.Log
        message: Severity below threshold, monitoring only
`

interface BlockEditorHostProps {
    initialParentPath?: string
    initialRefPath?: number
}

// Mirrors the real dock wiring (useNoCodePanels.ts / MultiPanelFlowEditorView.vue):
// BlockEditor only emits "editTask"/"closeTask"/"createTask" — the host owns the
// editingTask/parentPath/refPath/blockSchemaPath state and feeds it back as props.
// A Storybook mount of BlockEditor alone (without this host) can never open the
// inline TaskEdit panel, since BlockEditor's own template gates it on those props.
// When `initialParentPath` is set, the host starts directly in edit mode (the
// task-edit panel), otherwise it starts on the canvas like the real editor does.
const BlockEditorHost = defineComponent({
    name: "BlockEditorHost",
    props: {
        initialParentPath: {type: String, default: undefined},
        initialRefPath: {type: Number, default: undefined},
    },
    setup(props: BlockEditorHostProps) {
        const editingTask = ref(Boolean(props.initialParentPath))
        const parentPath = ref<string | undefined>(props.initialParentPath)
        const refPath = ref<number | undefined>(props.initialRefPath)
        const blockSchemaPath = ref<string>("#/definitions/io.kestra.core.models.flows.Flow/properties/tasks/items")
        const selectedId = ref<string>()

        function onEditTask(pPath: string, bSchemaPath: string, rPath?: number) {
            parentPath.value = pPath
            blockSchemaPath.value = bSchemaPath
            refPath.value = rPath
            editingTask.value = true
        }

        function onCloseTask() {
            editingTask.value = false
        }

        return () => (
            <div style="height: 760px; border: 1px solid var(--ks-border-default); border-radius: var(--ks-radius-base); overflow: hidden;">
                <BlockEditor
                    selectedId={selectedId.value}
                    onUpdate:selectedId={(id: string | undefined) => selectedId.value = id}
                    editingTask={editingTask.value}
                    parentPath={parentPath.value}
                    refPath={refPath.value}
                    blockSchemaPath={blockSchemaPath.value}
                    onEditTask={onEditTask}
                    onCloseTask={onCloseTask}
                />
            </div>
        )
    },
})

const makeHostRender = (yaml: string, edit?: BlockEditorHostProps): Story["render"] => () => ({
    setup() {
        const flowStore = useFlowStore()
        flowStore.flowYaml = yaml
        return () => <BlockEditorHost initialParentPath={edit?.initialParentPath} initialRefPath={edit?.initialRefPath} />
    },
})

const editModeDecorators = [
    vueRouter([
        {path: "/", name: "home", component: {template: "<div>home</div>"}},
        {path: "/flows", name: "flows", component: {template: "<div>flows</div>"}},
    ]),
]

const makeEditHostRender = (yaml: string, edit: BlockEditorHostProps): Story["render"] => () => ({
    setup() {
        mockNoCodeTransport()
        const flowStore = useFlowStore()
        flowStore.flowYaml = yaml
        return () => <BlockEditorHost initialParentPath={edit.initialParentPath} initialRefPath={edit.initialRefPath} />
    },
})

// F1 — Fullscreen exit affordance
const multiPanelArgs = (): Panel[] => [
    {
        activeTab: {
            button: {icon: markRaw(CodeTagsIcon), label: "Flow Code"},
            uid: "code",
            component: () => <div style="padding: 1rem; height: 50vh;">Flow Code content</div>,
        },
        size: 1,
        tabs: [
            {
                button: {icon: markRaw(CodeTagsIcon), label: "Flow Code"},
                uid: "code",
                component: () => <div style="padding: 1rem; height: 50vh;">Flow Code content</div>,
            },
        ],
    },
    {
        activeTab: {
            button: {icon: markRaw(FileTreeOutlineIcon), label: "Topology"},
            uid: "topology",
            component: () => <div style="padding: 1rem; height: 50vh;">Topology content</div>,
        },
        size: 1,
        tabs: [
            {
                button: {icon: markRaw(FileTreeOutlineIcon), label: "Topology"},
                uid: "topology",
                component: () => <div style="padding: 1rem; height: 50vh;">Topology content</div>,
            },
        ],
    },
]

export const F1FullscreenExit: Story = {
    render: () => ({
        setup() {
            const modelValue = ref<Panel[]>(multiPanelArgs())
            return () => (
                <div style="padding: 1rem; height: 60vh;">
                    <MultiPanelTabs modelValue={modelValue.value} />
                </div>
            )
        },
    }),
    parameters: {
        docs: {
            description: {
                story: "Problem: once a panel was maximized there was no visible way out, and the other tabs disappeared with no hint they still existed. Fix: the maximized panel now floats over thin, labeled slivers of its immediate neighbor panels — hovering widens them, clicking either one exits fullscreen.",
            },
        },
    },
    play: async ({canvasElement}) => {
        const maximizeButton = canvasElement.querySelectorAll("[data-test='panel-maximize']")[0] as HTMLElement
        await userEvent.click(maximizeButton)

        const sliver = await waitFor(() => {
            const el = canvasElement.querySelector("[data-test='maximized-sliver-right']") as HTMLElement
            expect(el).toBeInTheDocument()
            return el
        }, {timeout: 15000})
        expect(canvasElement.querySelector(".panel-maximized")).toBeInTheDocument()

        await userEvent.click(sliver)
        await waitFor(() => {
            expect(canvasElement.querySelector("[data-test='maximized-sliver-right']")).not.toBeInTheDocument()
        }, {timeout: 15000})
    },
}

// F2 — Run a task via the playground, from inside the real task-edit panel
export const F2PlaygroundRunTask: Story = {
    decorators: editModeDecorators,
    render: () => ({
        setup() {
            mockNoCodeTransport()
            const flowStore = useFlowStore()
            const playgroundStore = usePlaygroundStore()
            flowStore.flowYaml = CICD_PIPELINE_YAML
            playgroundStore.enabled = true
            return () => <BlockEditorHost initialParentPath="tasks" initialRefPath={1} />
        },
    }),
    parameters: {
        docs: {
            description: {
                story: "Problem: there was no way to run a single task from the no-code editor to iterate quickly. Fix: with the playground enabled, the real task-edit panel for the `build` step now shows a \"Run task\" split button (run this task / run task and downstream) above the form.",
            },
        },
    },
    play: async ({canvasElement}) => {
        const taskEdit = await waitFor(() => {
            const el = canvasElement.querySelector("[data-test='block-editor-task-edit']") as HTMLElement
            expect(el).toBeInTheDocument()
            return el
        }, {timeout: 15000})
        await waitFor(() => {
            expect(within(taskEdit).getAllByText("Run task").length).toBeGreaterThan(0)
        }, {timeout: 15000})
    },
}

// F3 — every flowable type gets a full, populated example, plus the Dag
// dependsOn editor and the Configure cog on the flowable's own properties.
export const F3CicdPipelineOverview: Story = {
    render: makeHostRender(CICD_PIPELINE_YAML),
    parameters: {
        docs: {
            description: {
                story: "Problem: flowables (If/Switch/Parallel/Sequential/Dag) were second-class citizens in no-code — hard to read, hard to configure. Fix: a realistic build → test → deploy pipeline, with a Parallel test stage and an If/then/else/errors/finally rollout gate, all fully editable from the canvas.",
            },
        },
    },
    play: async ({canvasElement}) => {
        const canvas = within(canvasElement)
        await waitFor(() => {
            expect(canvas.getByText("checkout")).toBeInTheDocument()
            expect(canvas.getByText("build")).toBeInTheDocument()
            expect(canvas.getByText("unit_tests")).toBeInTheDocument()
            expect(canvas.getByText("integration_tests")).toBeInTheDocument()
            expect(canvas.getByText("deploy_production")).toBeInTheDocument()
            expect(canvas.getByText("deploy_staging")).toBeInTheDocument()
            expect(canvas.getByText("notify_rollout_failure")).toBeInTheDocument()
            expect(canvas.getByText("record_deployment_metric")).toBeInTheDocument()
        }, {timeout: 15000})
    },
}

export const F3SwitchWithCasesAndDefaults: Story = {
    render: makeHostRender(SWITCH_YAML),
    parameters: {
        docs: {
            description: {
                story: "Problem: Switch blocks with several cases were unreadable and only editable via YAML. Fix: a ticket router with three real cases (critical/high/low) plus a defaults lane, each holding real tasks, fully editable in no-code.",
            },
        },
    },
    play: async ({canvasElement}) => {
        const canvas = within(canvasElement)
        await waitFor(() => {
            expect(canvas.getByText("page_oncall")).toBeInTheDocument()
            expect(canvas.getByText("notify_high_priority")).toBeInTheDocument()
            expect(canvas.getByText("queue_for_triage")).toBeInTheDocument()
            expect(canvas.getByText("log_unhandled_priority")).toBeInTheDocument()
        }, {timeout: 15000})
    },
}

export const F3SequentialSteps: Story = {
    render: makeHostRender(SEQUENTIAL_YAML),
    parameters: {
        docs: {
            description: {
                story: "Problem: Sequential blocks had the same no-code limitations as other flowables. Fix: a monthly report pipeline (fetch → build spreadsheet → email) rendered and editable as a Sequential cluster.",
            },
        },
    },
    play: async ({canvasElement}) => {
        const canvas = within(canvasElement)
        await waitFor(() => {
            expect(canvas.getByText("fetch_transactions")).toBeInTheDocument()
            expect(canvas.getByText("build_spreadsheet")).toBeInTheDocument()
            expect(canvas.getByText("email_finance_team")).toBeInTheDocument()
        }, {timeout: 15000})
    },
}

export const F3ForEachItemOverRealList: Story = {
    render: makeHostRender(FOREACH_ITEM_YAML),
    parameters: {
        docs: {
            description: {
                story: "Problem: ForEachItem, like other flowables, could not be configured in no-code. Fix: a cache-warming flow iterating over a real region list (`eu-west-1`, `us-east-1`, `ap-southeast-2`), with its inner task editable from the canvas.",
            },
        },
    },
    play: async ({canvasElement}) => {
        const canvas = within(canvasElement)
        await waitFor(() => {
            expect(canvas.getByText("warm_cache")).toBeInTheDocument()
        }, {timeout: 15000})
    },
}

export const F3DagEtlFanOutFanIn: Story = {
    render: makeHostRender(DAG_YAML),
    parameters: {
        docs: {
            description: {
                story: "Problem: a Dag task's sub-tasks (the `{task, dependsOn}` wrapper) rendered with blank ids and no way to manage dependencies in no-code. Fix: a realistic customer-360 ETL DAG with fan-out (two independent extracts) and fan-in (a merge step depending on both) — every sub-task shows its real id, and a `DagDependsOnEditor` control to pick dependencies among siblings.",
            },
        },
    },
    play: async ({canvasElement}) => {
        await waitFor(() => {
            const blockIds = [...canvasElement.querySelectorAll("[data-test='block-card-id']")].map(el => el.textContent)
            expect(blockIds).toEqual([
                "customer_360",
                "extract_crm",
                "extract_billing",
                "merge_datasets",
                "data_quality_check",
                "load_warehouse",
                "notify_data_team",
            ])
        }, {timeout: 15000})

        await waitFor(() => {
            const dependsOnControls = canvasElement.querySelectorAll("[data-test='dag-depends-on']")
            expect(dependsOnControls.length).toBe(6)
        }, {timeout: 15000})
    },
}

export const F3DeeplyNestedFlowables: Story = {
    render: makeHostRender(DEEPLY_NESTED_YAML),
    parameters: {
        docs: {
            description: {
                story: "Problem: flowables nested inside another flowable's branch (e.g. an If inside a Parallel inside an If) used to render incorrectly or not at all. Fix: an incident-response flow three levels deep — If > Parallel > If — renders and expands correctly at every level (depth pill appears past the cap).",
            },
        },
    },
    play: async ({canvasElement}) => {
        const canvas = within(canvasElement)
        // Clusters render expanded by default, and each level is an async
        // component that resolves a beat after its parent, so the deepest lanes
        // appear last: assert the depth-2 tasks first, then the depth-3 branch
        // tasks of the innermost If — proving recursion renders at every level.
        await waitFor(() => {
            expect(canvas.getByText("scale_up_service")).toBeInTheDocument()
            expect(canvas.getByText("check_customer_impact")).toBeInTheDocument()
        }, {timeout: 15000})

        await waitFor(() => {
            expect(canvas.getByText("notify_customer_success")).toBeInTheDocument()
            expect(canvas.getByText("log_minor_impact")).toBeInTheDocument()
        }, {timeout: 15000})
    },
}

export const F3ConfigureFlowableProperties: Story = {
    decorators: editModeDecorators,
    render: () => ({
        setup() {
            mockNoCodeTransport()
            seedIfTaskSchema()
            const flowStore = useFlowStore()
            flowStore.flowYaml = CICD_PIPELINE_YAML
            return () => <BlockEditorHost />
        },
    }),
    parameters: {
        docs: {
            description: {
                story: "Problem: an If/Switch/Parallel/Dag block's own properties (e.g. an If's `condition`) could not be edited from no-code — only its branches. Fix: clicking the flowable card's \"Configure\" cog opens the block's own inline task-edit form; here it opens the rollout gate's If block and exposes its `condition` for no-code editing.",
            },
        },
    },
    play: async ({canvasElement}) => {
        const rolloutHeader = await waitFor(() => {
            const header = [...canvasElement.querySelectorAll("[data-test='flowable-cluster-header']")]
                .find(el => within(el as HTMLElement).queryByText("rollout"))
            expect(header).toBeTruthy()
            return header as HTMLElement
        }, {timeout: 15000})

        rolloutHeader.focus()
        await waitFor(() => expect(document.activeElement).toBe(rolloutHeader))

        const configureButton = rolloutHeader.querySelector("[data-test='flowable-cluster-configure']") as HTMLElement
        await waitFor(() => expect(getComputedStyle(configureButton).pointerEvents).not.toBe("none"))
        await userEvent.click(configureButton)

        const taskEdit = await waitFor(() => {
            const el = canvasElement.querySelector("[data-test='block-editor-task-edit']") as HTMLElement
            expect(el).toBeInTheDocument()
            return el
        }, {timeout: 15000})

        await waitFor(() => {
            const fieldLabel = [...taskEdit.querySelectorAll(".label")].find(el => el.textContent?.trim() === "condition")
            expect(fieldLabel).toBeTruthy()
        }, {timeout: 15000})
    },
}

// F4 — Task Outputs panel, inside the real task-edit panel: read-only,
// collapsed by default, type-annotated, and hidden entirely when a task
// declares no outputs. Storybook has no backend to fetch the plugin schema
// from, so the Request type's documentation cache is pre-seeded with its
// real output shape — `pluginsStore.load()` checks this cache before ever
// reaching the (mocked, empty) network call, and TaskEditor calls `load()`
// on mount, which would otherwise clobber a value assigned after setup().
const HTTP_REQUEST_OUTPUTS_SCHEMA = {
    cls: "io.kestra.plugin.core.http.Request",
    schema: {
        outputs: {
            properties: {
                body: {type: "string"},
                encryptedBody: {type: "string"},
                headers: {type: "object"},
                code: {type: "integer"},
            },
        },
    } as any,
} as any

const seedHttpRequestOutputsSchema = () => {
    const pluginsStore = usePluginsStore()
    pluginsStore.pluginsDocumentation["io.kestra.plugin.core.http.Request"] = HTTP_REQUEST_OUTPUTS_SCHEMA
    pluginsStore.plugin = HTTP_REQUEST_OUTPUTS_SCHEMA
}

export const F4OutputsCollapsedByDefault: Story = {
    decorators: editModeDecorators,
    render: () => ({
        setup() {
            mockNoCodeTransport()
            seedHttpRequestOutputsSchema()
            const flowStore = useFlowStore()
            flowStore.flowYaml = CICD_PIPELINE_YAML
            return () => <BlockEditorHost initialParentPath="tasks[3].then" initialRefPath={0} />
        },
    }),
    parameters: {
        docs: {
            description: {
                story: "Problem: outputs were rendered as clickable, expanded chips even though a task's outputs are read-only and not always relevant. Fix: inside the real task-edit panel for `notify_release_channel` (an HTTP Request with four declared outputs — `body`, `encryptedBody`, `headers`, `code`), the Outputs column is now collapsed by default and shown as a rail — click it to expand.",
            },
        },
    },
    play: async ({canvasElement}) => {
        const taskEdit = await waitFor(() => {
            const el = canvasElement.querySelector("[data-test='block-editor-task-edit']") as HTMLElement
            expect(el).toBeInTheDocument()
            return el
        }, {timeout: 15000})
        const outputsRail = taskEdit.querySelector("[data-test='task-edit-data-output']") as HTMLElement
        expect(outputsRail).toBeInTheDocument()
        expect(outputsRail.tagName).toBe("BUTTON")
    },
}

export const F4OutputsExpandedReadOnly: Story = {
    decorators: editModeDecorators,
    render: () => ({
        setup() {
            mockNoCodeTransport()
            seedHttpRequestOutputsSchema()
            const flowStore = useFlowStore()
            flowStore.flowYaml = CICD_PIPELINE_YAML
            return () => <BlockEditorHost initialParentPath="tasks[3].then" initialRefPath={0} />
        },
    }),
    parameters: {
        docs: {
            description: {
                story: "Problem: output chips were clickable/draggable as if they inserted an expression, which is misleading since outputs are read-only, declared fields. Fix: expanded, every declared output of `notify_release_channel` (`body` — string, `encryptedBody` — string, `headers` — object, `code` — integer) renders as a static, non-interactive chip showing its name and type.",
            },
        },
    },
    play: async ({canvasElement}) => {
        const taskEdit = await waitFor(() => {
            const el = canvasElement.querySelector("[data-test='block-editor-task-edit']") as HTMLElement
            expect(el).toBeInTheDocument()
            return el
        }, {timeout: 15000})

        const collapseRail = taskEdit.querySelector("[data-test='task-edit-data-output']") as HTMLElement
        await userEvent.click(collapseRail)

        const outputsPanel = await waitFor(() => {
            const el = taskEdit.querySelector("[data-test='task-edit-data-output']") as HTMLElement
            expect(el.tagName).toBe("DIV")
            return el
        }, {timeout: 15000})

        for (const label of ["body", "encryptedBody", "headers", "code"]) {
            const chip = within(outputsPanel).getByText(label).closest(".task-edit-data-chip")
            expect(chip?.classList.contains("task-edit-data-chip--static")).toBe(true)
            expect(chip?.tagName).toBe("DIV")
        }
    },
}

export const F4OutputsHiddenWhenNone: Story = {
    decorators: editModeDecorators,
    render: makeEditHostRender(CICD_PIPELINE_YAML, {initialParentPath: "tasks[3].errors", initialRefPath: 0}),
    parameters: {
        docs: {
            description: {
                story: "Problem: the Outputs column was always shown, even for a task that declares no outputs. Fix: inside the task-edit panel for `notify_rollout_failure` (a plain `Log` task), `TaskEdit`'s `outputSections` computed is empty, so the whole Outputs column is not rendered at all.",
            },
        },
    },
    play: async ({canvasElement}) => {
        const taskEdit = await waitFor(() => {
            const el = canvasElement.querySelector("[data-test='block-editor-task-edit']") as HTMLElement
            expect(el).toBeInTheDocument()
            return el
        }, {timeout: 15000})
        expect(taskEdit.querySelector("[data-test='task-edit-data-output']")).not.toBeInTheDocument()
    },
}

// F5 — Source tab fidelity: raw YAML slice preserved verbatim across
// comments, guillemets, a multi-line block scalar, and a blank line. The
// pipeline's own `notify_release_channel` task carries this formatting, so
// the real BlockEditor extracts and displays it exactly as authored.
const CICD_PIPELINE_WITH_RICH_FORMATTING_YAML = CICD_PIPELINE_YAML.replace(
    `      - id: notify_release_channel
        type: io.kestra.plugin.core.http.Request
        uri: https://hooks.example.com/release
        method: POST
        contentType: application/json
        body: "{{ {'service': 'payments', 'status': 'deploying'} | toJson }}"`,
    `      - id: notify_release_channel
        type: io.kestra.plugin.core.http.Request
        # Posted to the #releases channel — keep this note when editing the payload
        uri: https://hooks.example.com/release

        method: POST
        contentType: application/json
        # The message below intentionally uses French guillemets for the release note
        body: |
          Déploiement « payments » terminé.
          Statut : succès.`,
)

export const F5SourceFidelity: Story = {
    decorators: editModeDecorators,
    render: makeEditHostRender(CICD_PIPELINE_WITH_RICH_FORMATTING_YAML, {initialParentPath: "tasks[3].then", initialRefPath: 0}),
    parameters: {
        docs: {
            description: {
                story: "Problem: the Source tab re-serialized the parsed task, dropping YAML comments and re-escaping strings (e.g. « guillemets » or multi-line block scalars), so it never round-tripped byte-for-byte with Flow Code. Fix: opening `notify_release_channel` in the real task-edit panel shows its Source tab preserving both comments, the blank line, and the multi-line « guillemets » body exactly as written in Flow Code.",
            },
        },
    },
    play: async ({canvasElement}) => {
        const taskEdit = await waitFor(() => {
            const el = canvasElement.querySelector("[data-test='block-editor-task-edit']") as HTMLElement
            expect(el).toBeInTheDocument()
            return el
        }, {timeout: 15000})

        const sourceTab = within(taskEdit).getByText("Source")
        await userEvent.click(sourceTab)

        const textarea = await waitFor(() => {
            const el = taskEdit.querySelector("[data-testid='monaco-editor-hidden-synced-textarea']") as HTMLTextAreaElement
            expect(el).toBeInTheDocument()
            return el
        }, {timeout: 15000})

        await waitFor(() => {
            expect(textarea.value).toContain("# Posted to the #releases channel — keep this note when editing the payload")
            expect(textarea.value).toContain("# The message below intentionally uses French guillemets for the release note")
            expect(textarea.value).toContain("Déploiement « payments » terminé.")
            expect(textarea.value).toContain("Statut : succès.")
            expect(textarea.value).toContain("\n\nmethod: POST")
        }, {timeout: 15000})
    },
}

// F6 — Documentation panel: pending design item, current state only
export const F6DocumentationCurrentState: Story = {
    render: () => ({
        setup() {
            const axios: any = {get: () => Promise.resolve({data: [], status: 200, headers: {}})}
            setMockClient(axios)
            return () => <PluginDocumentation overrideIntro="This is the documentation panel's current implementation." />
        },
    }),
    parameters: {
        docs: {
            description: {
                story: "Not a code fix. The requested documentation-panel redesign (sticky header, section navigation, filterable properties reference) is a pending design follow-up — a mockup exists outside this repo but no implementation has landed yet. This story only shows the CURRENT `PluginDocumentation` panel for reference; do not read it as a resolution of the feedback item.",
            },
        },
    },
}

// F7 — "/" opens the command menu with typed filters
export const F7QuickInsertCommandMenu: Story = {
    render: makeHostRender(CICD_PIPELINE_YAML),
    parameters: {
        docs: {
            description: {
                story: "Problem: there was no fast, keyboard-only way to discover and insert a task/trigger/error handler from the canvas. Fix: pressing \"/\" opens a command menu; typing a keyword (e.g. \"trigger\") filters commands down to the matching \"Insert …\" action.",
            },
        },
    },
    play: async ({canvasElement}) => {
        const canvas = within(canvasElement)

        await waitFor(() => {
            expect(canvas.getByText("build")).toBeInTheDocument()
        }, {timeout: 15000})

        await userEvent.keyboard("/")

        const search = await waitFor(() => {
            // The command menu teleports to <body>, outside canvasElement — query
            // the document, not the (canvas-scoped) `canvas`. KsInput also forwards
            // $attrs straight onto ElInput's native <input>, so data-test lands on
            // the input itself, not on a wrapper around it.
            const el = document.querySelector("[data-test='block-command-menu-search']") as HTMLInputElement
            expect(el).toBeInTheDocument()
            return el
        }, {timeout: 15000})
        expect(document.activeElement).toBe(search)
        expect(search.placeholder).toBe("Type a command or search a task…")

        await userEvent.type(search, "trigger")

        await waitFor(() => {
            expect(search.value).toBe("trigger")
        }, {timeout: 15000})

        await waitFor(() => {
            expect(within(document.body).getByText("Insert Triggers")).toBeInTheDocument()
        }, {timeout: 15000})
    },
}

// F9 — pluginDefaults for two fields on the SAME task type, plus a second
// task instance, so several hints show at once without polluting the task
// YAML. Grafted onto the real CI/CD pipeline (pluginDefaults is a flow-level
// key read by every task in it, regardless of nesting).
const CICD_PIPELINE_WITH_PLUGIN_DEFAULTS_YAML = CICD_PIPELINE_YAML.replace(
    "description: Build, test and deploy the payments service on every push to main.\n",
    `description: Build, test and deploy the payments service on every push to main.
pluginDefaults:
  - type: io.kestra.plugin.core.log.Log
    values:
      level: DEBUG
      allowFailure: true
`,
)

// Schema defaults render a hint on every field carrying one, so a bare text
// match on "default: …" hits several fields at once — scope each hint to the
// field that owns it.
function defaultHintFor(taskEdit: HTMLElement, field: string) {
    return taskEdit.querySelector(`[data-test='field-${field}'] [data-test='field-default-hint']`)
}

export const F9PluginDefaultsHint: Story = {
    decorators: editModeDecorators,
    render: makeEditHostRender(CICD_PIPELINE_WITH_PLUGIN_DEFAULTS_YAML, {initialParentPath: "tasks[3].errors", initialRefPath: 0}),
    parameters: {
        docs: {
            description: {
                story: "Problem: flow-level `pluginDefaults` were invisible in the no-code task form, so users could not tell a field would fall back to a default at runtime. Fix: `TaskEditor` matches `pluginDefaults` by task type and provides them to the form. Opening `notify_rollout_failure` (a `Log` task) in the real task-edit panel shows both its `level` and `allowFailure` fields with \"(default: …)\" hints at once — without writing those values into the task's own YAML.",
            },
        },
    },
    play: async ({canvasElement}) => {
        const taskEdit = await waitFor(() => {
            const el = canvasElement.querySelector("[data-test='block-editor-task-edit']") as HTMLElement
            expect(el).toBeInTheDocument()
            return el
        }, {timeout: 15000})
        await waitFor(() => {
            expect(defaultHintFor(taskEdit, "level")).toHaveTextContent("default: DEBUG")
            expect(defaultHintFor(taskEdit, "allowFailure")).toHaveTextContent("default: true")
        }, {timeout: 15000})
    },
}

export const F9PluginDefaultsOnAnotherTaskInstance: Story = {
    decorators: editModeDecorators,
    render: makeEditHostRender(CICD_PIPELINE_WITH_PLUGIN_DEFAULTS_YAML, {initialParentPath: "tasks[3].finally", initialRefPath: 0}),
    parameters: {
        docs: {
            description: {
                story: "Problem: it wasn't obvious pluginDefaults apply flow-wide, to every matching task, not just one. Fix: opening a second `Log` task (`record_deployment_metric`, nested in the rollout's `finally` lane) shows the SAME `level`/`allowFailure` default hints, proving the match is by type across the whole flow.",
            },
        },
    },
    play: async ({canvasElement}) => {
        const taskEdit = await waitFor(() => {
            const el = canvasElement.querySelector("[data-test='block-editor-task-edit']") as HTMLElement
            expect(el).toBeInTheDocument()
            return el
        }, {timeout: 15000})
        await waitFor(() => {
            expect(defaultHintFor(taskEdit, "level")).toHaveTextContent("default: DEBUG")
            expect(defaultHintFor(taskEdit, "allowFailure")).toHaveTextContent("default: true")
        }, {timeout: 15000})
    },
}

// F10 — "?" help shortcut listed FIRST in the footer
export const F10HelpFirstInFooter: Story = {
    render: makeHostRender(CICD_PIPELINE_YAML),
    parameters: {
        docs: {
            description: {
                story: "Problem: the \"?\" keyboard-shortcuts help was buried at the end of the footer's hint list, easy to miss on first use. Fix: the footer now lists the \"?\" help hint FIRST, before move/open/insert/command-menu hints.",
            },
        },
    },
    play: async ({canvasElement}) => {
        const footer = canvasElement.querySelector("[data-test='block-editor-footer']") as HTMLElement
        expect(footer).toBeInTheDocument()

        const hints = footer.querySelectorAll(".block-editor-footer-hint")
        expect(hints.length).toBeGreaterThan(0)
        expect(hints[0].textContent).toContain("This help")
    },
}
