import type {Meta, StoryFn, StoryObj} from "@storybook/vue3-vite"
import {expect} from "storybook/test"
import {Topology, type FlowGraph} from "@kestra-io/topology"
import allowFailureDemo from "../../../fixtures/flowgraphs/allow-failure-demo.json"
import eachSequential from "../../../fixtures/flowgraphs/each-sequential.json"
import switchCaseLabels from "../../../fixtures/flowgraphs/switch-case-labels.json"

const ALLOW_FAILURE_SOURCE = `
id: allow-failure-demo
namespace: tutorial
tasks:
  - id: allow_failure
    type: io.kestra.plugin.core.flow.AllowFailure
    tasks:
      - id: fail_silently
        type: io.kestra.plugin.scripts.shell.Commands
        commands:
          - exit 1
  - id: print_to_console
    type: io.kestra.plugin.scripts.shell.Commands
    commands:
      - echo "this will run since previous failure was allowed"
`.trim()

const EACH_SEQUENTIAL_SOURCE = `
id: each-sequential
namespace: tutorial
tasks:
  - id: 1_each
    type: io.kestra.plugin.core.flow.EachSequential
    value: '["value 1", "value 2", "value 3"]'
    tasks:
      - id: 1-1
        type: io.kestra.plugin.core.debug.Return
        format: "{{task.id}} > {{taskrun.value}}"
      - id: 1-2
        type: io.kestra.plugin.core.debug.Return
        format: "{{task.id}} > {{taskrun.value}}"
  - id: 2_each
    type: io.kestra.plugin.core.flow.EachSequential
    value: '["value 1", "value 2", "value 3"]'
    tasks:
      - id: 2-1
        type: io.kestra.plugin.core.debug.Return
        format: "{{task.id}} > {{taskrun.value}}"
`.trim()

const STATUS_SHOWCASE_SOURCE = `
id: topology_status_showcase
namespace: qa.topology
tasks:
  - id: log_start
    type: io.kestra.plugin.core.log.Log
    message: "Topology status showcase started"
  - id: parallel_status_showcase
    type: io.kestra.plugin.core.flow.Parallel
    tasks:
      - id: branch_success
        type: io.kestra.plugin.core.log.Log
        message: "Branch that succeeds normally"
      - id: branch_warning
        type: io.kestra.plugin.core.execution.Fail
        allowFailure: true
        errorMessage: "Soft fail - parent should render as WARNING"
      - id: branch_loop
        type: io.kestra.plugin.core.flow.Loop
        values: ["alpha", "beta", "gamma"]
        tasks:
          - id: loop_iteration_log
            type: io.kestra.plugin.core.log.Log
            message: "Loop iteration value={{ item.value }}"
      - id: branch_subflow
        type: io.kestra.plugin.core.flow.Subflow
        namespace: qa.topology
        flowId: topology_status_showcase_child
        wait: true
      - id: branch_paused
        type: io.kestra.plugin.core.flow.Pause
      - id: branch_long_sleep_kill
        type: io.kestra.plugin.core.flow.Sleep
        duration: PT5M
  - id: hard_fail_after_parallel
    type: io.kestra.plugin.core.execution.Fail
    errorMessage: "Hard fail after Parallel - this is the FAILED node"
  - id: log_unreached
    type: io.kestra.plugin.core.log.Log
    message: "This task should appear as un-executed in topology"
errors:
  - id: error_handler_log
    type: io.kestra.plugin.core.log.Log
    message: "Errors branch handled"
`.trim()

// flowGraph fetched from local Kestra instance: qa.topology/topology_status_showcase
const STATUS_SHOWCASE_GRAPH = {"nodes":[{"uid":"root.root-6VTGjRXbrltI3rNOtRcumj","type":"io.kestra.core.models.hierarchies.GraphClusterRoot"},{"uid":"root.end-2am3S4TvUJfoMXHGfnmBVI","type":"io.kestra.core.models.hierarchies.GraphClusterEnd"},{"uid":"root.log_start","type":"io.kestra.core.models.hierarchies.GraphTask","task":{"id":"log_start","type":"io.kestra.plugin.core.log.Log","message":"Topology status showcase started"},"relationType":"SEQUENTIAL"},{"uid":"root.parallel_status_showcase.root-2nOxiekcwmId6t69VCONjQ","type":"io.kestra.core.models.hierarchies.GraphClusterRoot"},{"uid":"root.parallel_status_showcase.end-3mf0vf9Ol9bVcIEK0Il9BR","type":"io.kestra.core.models.hierarchies.GraphClusterEnd"},{"uid":"root.parallel_status_showcase","type":"io.kestra.core.models.hierarchies.GraphTask","task":{"id":"parallel_status_showcase","type":"io.kestra.plugin.core.flow.Parallel","tasks":[{"id":"branch_success","type":"io.kestra.plugin.core.log.Log","message":"Branch that succeeds normally"},{"id":"branch_warning","type":"io.kestra.plugin.core.execution.Fail","allowFailure":true,"errorMessage":"Soft fail - parent should render as WARNING"},{"id":"branch_loop","type":"io.kestra.plugin.core.flow.Loop","tasks":[{"id":"loop_iteration_log","type":"io.kestra.plugin.core.log.Log","message":"Loop iteration value={{ item.value }}"}],"values":["alpha","beta","gamma"]},{"id":"branch_subflow","type":"io.kestra.plugin.core.flow.Subflow","namespace":"qa.topology","flowId":"topology_status_showcase_child"},{"id":"branch_paused","type":"io.kestra.plugin.core.flow.Pause"},{"id":"branch_long_sleep_kill","type":"io.kestra.plugin.core.flow.Sleep","duration":"PT5M"}]},"relationType":"PARALLEL"},{"uid":"root.parallel_status_showcase.branch_success","type":"io.kestra.core.models.hierarchies.GraphTask","task":{"id":"branch_success","type":"io.kestra.plugin.core.log.Log","message":"Branch that succeeds normally"},"relationType":"PARALLEL"},{"uid":"root.parallel_status_showcase.branch_warning","type":"io.kestra.core.models.hierarchies.GraphTask","task":{"id":"branch_warning","type":"io.kestra.plugin.core.execution.Fail","allowFailure":true,"errorMessage":"Soft fail - parent should render as WARNING"},"relationType":"PARALLEL"},{"uid":"root.parallel_status_showcase.branch_loop.root-51RtbtnVgFwYUrGNqksXUI","type":"io.kestra.core.models.hierarchies.GraphClusterRoot"},{"uid":"root.parallel_status_showcase.branch_loop.end-4pzvGyAIDzGia4MnX1svpF","type":"io.kestra.core.models.hierarchies.GraphClusterEnd"},{"uid":"root.parallel_status_showcase.branch_loop","type":"io.kestra.core.models.hierarchies.GraphTask","task":{"id":"branch_loop","type":"io.kestra.plugin.core.flow.Loop","tasks":[{"id":"loop_iteration_log","type":"io.kestra.plugin.core.log.Log","message":"Loop iteration value={{ item.value }}"}],"values":["alpha","beta","gamma"]},"relationType":"DYNAMIC"},{"uid":"root.parallel_status_showcase.branch_loop.loop_iteration_log","type":"io.kestra.core.models.hierarchies.GraphTask","task":{"id":"loop_iteration_log","type":"io.kestra.plugin.core.log.Log","message":"Loop iteration value={{ item.value }}"},"relationType":"SEQUENTIAL"},{"uid":"root.parallel_status_showcase.branch_subflow","type":"io.kestra.core.models.hierarchies.SubflowGraphTask","task":{"id":"branch_subflow","type":"io.kestra.plugin.core.flow.Subflow","namespace":"qa.topology","flowId":"topology_status_showcase_child"},"relationType":"PARALLEL"},{"uid":"root.parallel_status_showcase.branch_paused","type":"io.kestra.core.models.hierarchies.GraphTask","task":{"id":"branch_paused","type":"io.kestra.plugin.core.flow.Pause"},"relationType":"SEQUENTIAL"},{"uid":"root.parallel_status_showcase.branch_long_sleep_kill","type":"io.kestra.core.models.hierarchies.GraphTask","task":{"id":"branch_long_sleep_kill","type":"io.kestra.plugin.core.flow.Sleep","duration":"PT5M"},"relationType":"PARALLEL"},{"uid":"root.hard_fail_after_parallel","type":"io.kestra.core.models.hierarchies.GraphTask","task":{"id":"hard_fail_after_parallel","type":"io.kestra.plugin.core.execution.Fail","errorMessage":"Hard fail after Parallel - this is the FAILED node"},"relationType":"SEQUENTIAL"},{"uid":"root.log_unreached","type":"io.kestra.core.models.hierarchies.GraphTask","task":{"id":"log_unreached","type":"io.kestra.plugin.core.log.Log","message":"This task should appear as un-executed in topology"},"relationType":"SEQUENTIAL"},{"uid":"root.error_handler_log","type":"io.kestra.core.models.hierarchies.GraphTask","branchType":"ERROR","task":{"id":"error_handler_log","type":"io.kestra.plugin.core.log.Log","message":"Errors branch handled: {{ errorMessage ?? 'unknown' }}"},"relationType":"ERROR"}],"edges":[{"source":"root.hard_fail_after_parallel","target":"root.log_unreached","relation":{"relationType":"SEQUENTIAL"}},{"source":"root.log_unreached","target":"root.end-2am3S4TvUJfoMXHGfnmBVI","relation":{}},{"source":"root.log_start","target":"root.parallel_status_showcase.root-2nOxiekcwmId6t69VCONjQ","relation":{"relationType":"SEQUENTIAL"}},{"source":"root.parallel_status_showcase.end-3mf0vf9Ol9bVcIEK0Il9BR","target":"root.hard_fail_after_parallel","relation":{"relationType":"SEQUENTIAL"}},{"source":"root.error_handler_log","target":"root.end-2am3S4TvUJfoMXHGfnmBVI","relation":{}},{"source":"root.root-6VTGjRXbrltI3rNOtRcumj","target":"root.log_start","relation":{}},{"source":"root.root-6VTGjRXbrltI3rNOtRcumj","target":"root.error_handler_log","relation":{"relationType":"ERROR"}},{"source":"root.parallel_status_showcase","target":"root.parallel_status_showcase.branch_warning","relation":{"relationType":"PARALLEL"}},{"source":"root.parallel_status_showcase.branch_loop.end-4pzvGyAIDzGia4MnX1svpF","target":"root.parallel_status_showcase.end-3mf0vf9Ol9bVcIEK0Il9BR","relation":{}},{"source":"root.parallel_status_showcase","target":"root.parallel_status_showcase.branch_subflow","relation":{"relationType":"PARALLEL"}},{"source":"root.parallel_status_showcase.branch_warning","target":"root.parallel_status_showcase.end-3mf0vf9Ol9bVcIEK0Il9BR","relation":{}},{"source":"root.parallel_status_showcase.branch_long_sleep_kill","target":"root.parallel_status_showcase.end-3mf0vf9Ol9bVcIEK0Il9BR","relation":{}},{"source":"root.parallel_status_showcase.branch_subflow","target":"root.parallel_status_showcase.end-3mf0vf9Ol9bVcIEK0Il9BR","relation":{}},{"source":"root.parallel_status_showcase.root-2nOxiekcwmId6t69VCONjQ","target":"root.parallel_status_showcase","relation":{}},{"source":"root.parallel_status_showcase","target":"root.parallel_status_showcase.branch_loop.root-51RtbtnVgFwYUrGNqksXUI","relation":{"relationType":"PARALLEL"}},{"source":"root.parallel_status_showcase","target":"root.parallel_status_showcase.branch_success","relation":{"relationType":"PARALLEL"}},{"source":"root.parallel_status_showcase.branch_success","target":"root.parallel_status_showcase.end-3mf0vf9Ol9bVcIEK0Il9BR","relation":{}},{"source":"root.parallel_status_showcase","target":"root.parallel_status_showcase.branch_paused","relation":{"relationType":"PARALLEL"}},{"source":"root.parallel_status_showcase","target":"root.parallel_status_showcase.branch_long_sleep_kill","relation":{"relationType":"PARALLEL"}},{"source":"root.parallel_status_showcase.branch_paused","target":"root.parallel_status_showcase.end-3mf0vf9Ol9bVcIEK0Il9BR","relation":{}},{"source":"root.parallel_status_showcase.branch_loop.root-51RtbtnVgFwYUrGNqksXUI","target":"root.parallel_status_showcase.branch_loop","relation":{}},{"source":"root.parallel_status_showcase.branch_loop","target":"root.parallel_status_showcase.branch_loop.loop_iteration_log","relation":{"relationType":"DYNAMIC"}},{"source":"root.parallel_status_showcase.branch_loop.loop_iteration_log","target":"root.parallel_status_showcase.branch_loop.end-4pzvGyAIDzGia4MnX1svpF","relation":{}}],"clusters":[{"cluster":{"uid":"cluster_root.parallel_status_showcase","type":"io.kestra.core.models.hierarchies.GraphCluster","relationType":"PARALLEL","taskNode":{"uid":"root.parallel_status_showcase","type":"io.kestra.core.models.hierarchies.GraphTask","task":{"id":"parallel_status_showcase","type":"io.kestra.plugin.core.flow.Parallel","tasks":[{"id":"branch_success","type":"io.kestra.plugin.core.log.Log","message":"Branch that succeeds normally"},{"id":"branch_warning","type":"io.kestra.plugin.core.execution.Fail","allowFailure":true,"errorMessage":"Soft fail - parent should render as WARNING"},{"id":"branch_loop","type":"io.kestra.plugin.core.flow.Loop","tasks":[{"id":"loop_iteration_log","type":"io.kestra.plugin.core.log.Log","message":"Loop iteration value={{ item.value }}"}],"values":["alpha","beta","gamma"]},{"id":"branch_subflow","type":"io.kestra.plugin.core.flow.Subflow","namespace":"qa.topology","flowId":"topology_status_showcase_child"},{"id":"branch_paused","type":"io.kestra.plugin.core.flow.Pause"},{"id":"branch_long_sleep_kill","type":"io.kestra.plugin.core.flow.Sleep","duration":"PT5M"}]},"relationType":"PARALLEL"},"finally":{"uid":"parallel_status_showcase.finally-2JF59vqtxtXWU9SUKtes7D","type":"io.kestra.core.models.hierarchies.GraphClusterFinally"}},"nodes":["root.parallel_status_showcase.root-2nOxiekcwmId6t69VCONjQ","root.parallel_status_showcase.end-3mf0vf9Ol9bVcIEK0Il9BR","root.parallel_status_showcase","root.parallel_status_showcase.branch_success","root.parallel_status_showcase.branch_warning","cluster_root.parallel_status_showcase.branch_loop","root.parallel_status_showcase.branch_loop.root-51RtbtnVgFwYUrGNqksXUI","root.parallel_status_showcase.branch_loop.end-4pzvGyAIDzGia4MnX1svpF","root.parallel_status_showcase.branch_subflow","root.parallel_status_showcase.branch_paused","root.parallel_status_showcase.branch_long_sleep_kill"],"parents":[],"start":"root.parallel_status_showcase.root-2nOxiekcwmId6t69VCONjQ","end":"root.parallel_status_showcase.end-3mf0vf9Ol9bVcIEK0Il9BR"},{"cluster":{"uid":"cluster_root.parallel_status_showcase.branch_loop","type":"io.kestra.core.models.hierarchies.GraphCluster","relationType":"DYNAMIC","taskNode":{"uid":"root.parallel_status_showcase.branch_loop","type":"io.kestra.core.models.hierarchies.GraphTask","task":{"id":"branch_loop","type":"io.kestra.plugin.core.flow.Loop","tasks":[{"id":"loop_iteration_log","type":"io.kestra.plugin.core.log.Log","message":"Loop iteration value={{ item.value }}"}],"values":["alpha","beta","gamma"]},"relationType":"DYNAMIC"},"finally":{"uid":"branch_loop.finally-5FjGaF9hw4qCyqAdqYUrcx","type":"io.kestra.core.models.hierarchies.GraphClusterFinally"}},"nodes":["root.parallel_status_showcase.branch_loop.root-51RtbtnVgFwYUrGNqksXUI","root.parallel_status_showcase.branch_loop.end-4pzvGyAIDzGia4MnX1svpF","root.parallel_status_showcase.branch_loop","root.parallel_status_showcase.branch_loop.loop_iteration_log"],"parents":["cluster_root.parallel_status_showcase"],"start":"root.parallel_status_showcase.branch_loop.root-51RtbtnVgFwYUrGNqksXUI","end":"root.parallel_status_showcase.branch_loop.end-4pzvGyAIDzGia4MnX1svpF"}]}

const SWITCH_CASE_LABELS_SOURCE = `
id: render-language
namespace: company.team
inputs:
  - id: lang
    type: STRING
    defaults: French
tasks:
  - id: render-language
    type: io.kestra.plugin.core.flow.Switch
    value: "{{ inputs.lang }}"
    cases:
      French:
        - id: french
          type: io.kestra.plugin.core.debug.Return
          format: Bonjour
      German:
        - id: german
          type: io.kestra.plugin.core.debug.Return
          format: Hallo
    defaults:
      - id: english
        type: io.kestra.plugin.core.debug.Return
        format: Hello
`.trim()

export default {
    title: "Components/Topology/Topology",
    component: Topology,
    parameters: {
        layout: "fullscreen",
    },
    argTypes: {
        isHorizontal: {control: "boolean"},
        isReadOnly: {control: "boolean"},
    },
} as Meta<typeof Topology>

const Template: StoryFn<typeof Topology> = (args) => ({
    components: {Topology},
    setup() {
        return {args}
    },
    template: "<div style=\"height: 600px; width: 100%;\"><Topology v-bind=\"args\" /></div>",
})

export const AllowFailure = Template.bind({})
AllowFailure.args = {
    id: "story-allow-failure",
    source: ALLOW_FAILURE_SOURCE,
    flowGraph: allowFailureDemo as unknown as FlowGraph,
    isReadOnly: true,
    isHorizontal: true,
}

export const EachSequential = Template.bind({})
EachSequential.args = {
    id: "story-each-sequential",
    source: EACH_SEQUENTIAL_SOURCE,
    flowGraph: eachSequential as unknown as FlowGraph,
    isReadOnly: true,
    isHorizontal: true,
}

export const Vertical = Template.bind({})
Vertical.args = {
    id: "story-vertical",
    source: ALLOW_FAILURE_SOURCE,
    flowGraph: allowFailureDemo as unknown as FlowGraph,
    isReadOnly: true,
    isHorizontal: false,
}

export const StatusShowcase = Template.bind({})
StatusShowcase.storyName = "Status Showcase"
StatusShowcase.args = {
    id: "story-status-showcase",
    source: STATUS_SHOWCASE_SOURCE,
    flowGraph: STATUS_SHOWCASE_GRAPH as unknown as FlowGraph,
    isReadOnly: true,
    isHorizontal: true,
}

export const SwitchCaseLabels = Template.bind({})
SwitchCaseLabels.storyName = "Switch Case Labels"
SwitchCaseLabels.args = {
    id: "story-switch-case-labels",
    source: SWITCH_CASE_LABELS_SOURCE,
    flowGraph: switchCaseLabels as unknown as FlowGraph,
    isReadOnly: true,
    isHorizontal: false,
}

export const ValidationIssues = Template.bind({})
ValidationIssues.storyName = "Validation Issues"
ValidationIssues.args = {
    id: "story-validation-issues",
    source: ALLOW_FAILURE_SOURCE,
    flowGraph: allowFailureDemo as unknown as FlowGraph,
    isReadOnly: true,
    isHorizontal: true,
    // allow_failure: no entry, so no badge. fail_silently: a single issue. print_to_console: several.
    validationIssuesByTask: new Map([
        ["fail_silently", ["commands: must not be empty"]],
        ["print_to_console", [
            "commands: must not be empty",
            "runner: unsupported task runner type",
            "timeout: must be a valid ISO 8601 duration",
        ]],
    ]),
}

// The Parallel's direct children land on purpose-mixed states: the lane header must surface the
// worst of them (WARNING, ranked above RUNNING) rather than the first or the last child.
function taskRun(taskId: string, state: string, id = `${taskId}-run`) {
    return {id, taskId, state: {current: state}}
}

const MIXED_LANE_EXECUTION = {
    id: "story-execution-mixed-lane",
    state: {current: "RUNNING"},
    taskRunList: [
        taskRun("log_start", "SUCCESS"),
        taskRun("parallel_status_showcase", "RUNNING"),
        taskRun("branch_success", "SUCCESS"),
        taskRun("branch_warning", "WARNING"),
        taskRun("branch_loop", "SUCCESS"),
        taskRun("loop_iteration_log", "SUCCESS", "loop-1"),
        taskRun("loop_iteration_log", "SUCCESS", "loop-2"),
        taskRun("loop_iteration_log", "SUCCESS", "loop-3"),
        taskRun("branch_subflow", "RUNNING"),
        // branch_paused and branch_long_sleep_kill: no task run yet — still queued behind the
        // other Parallel branches, so the lane's child count already shows 6 while only 4 ran.
    ],
}

// Mirrors the `executionId` the app stamps onto every node once an execution is loaded
// (executions.ts's loadAugmentedGraph) — without it, TaskNode/ClusterNode never resolve a
// task run for a node, and the story would render as if no execution existed at all.
function withExecutionId(flowGraph: typeof STATUS_SHOWCASE_GRAPH, executionId: string) {
    return {
        ...flowGraph,
        nodes: flowGraph.nodes.map((node) => ({...node, executionId})),
    }
}

const MIXED_LANE_GRAPH = withExecutionId(STATUS_SHOWCASE_GRAPH, MIXED_LANE_EXECUTION.id)

export const LaneHeaderMixedState = Template.bind({})
LaneHeaderMixedState.storyName = "Lane Header — Mixed Child State"
LaneHeaderMixedState.args = {
    id: "story-lane-header-mixed",
    source: STATUS_SHOWCASE_SOURCE,
    flowGraph: MIXED_LANE_GRAPH as unknown as FlowGraph,
    isReadOnly: true,
    isHorizontal: true,
    execution: MIXED_LANE_EXECUTION,
}

export const ErrorsLane = Template.bind({})
ErrorsLane.storyName = "Errors Lane"
ErrorsLane.args = {
    id: "story-errors-lane",
    source: STATUS_SHOWCASE_SOURCE,
    // A flow-level `errors:` task (`error_handler_log`) has no cluster of its own in the graph the
    // backend sends — this is exactly what makes it render as a labelled lane rather than a
    // floating dashed line (kestra-io/kestra#19666).
    flowGraph: STATUS_SHOWCASE_GRAPH as unknown as FlowGraph,
    isReadOnly: true,
    isHorizontal: true,
}

const LodTemplate: StoryFn<typeof Topology> = (args) => ({
    components: {Topology},
    setup() {
        return {args}
    },
    template: `<div style="height: 600px; width: 100%;">
        <Topology v-bind="args">
            <template #taskDetails="taskProps">
                <div style="padding: 8px; font-size: 12px; max-width: 16rem;">
                    Plugin-specific details for {{ taskProps.data.node.task.id }} would render here.
                </div>
            </template>
        </Topology>
    </div>`,
})

export const LevelOfDetailPill = LodTemplate.bind({})
LevelOfDetailPill.storyName = "Level of Detail — Pill (< 50% zoom)"
LevelOfDetailPill.args = {
    id: "story-lod-pill",
    source: STATUS_SHOWCASE_SOURCE,
    flowGraph: MIXED_LANE_GRAPH as unknown as FlowGraph,
    isReadOnly: true,
    isHorizontal: true,
    execution: MIXED_LANE_EXECUTION,
    defaultViewport: {x: 0, y: 0, zoom: 0.3},
}

export const LevelOfDetailDefault = LodTemplate.bind({})
LevelOfDetailDefault.storyName = "Level of Detail — Default"
LevelOfDetailDefault.args = {
    id: "story-lod-default",
    source: STATUS_SHOWCASE_SOURCE,
    flowGraph: MIXED_LANE_GRAPH as unknown as FlowGraph,
    isReadOnly: true,
    isHorizontal: true,
    execution: MIXED_LANE_EXECUTION,
    defaultViewport: {x: 0, y: 0, zoom: 1},
}

export const LevelOfDetailExpanded = LodTemplate.bind({})
LevelOfDetailExpanded.storyName = "Level of Detail — Expanded (> 130% zoom)"
LevelOfDetailExpanded.args = {
    id: "story-lod-expanded",
    source: STATUS_SHOWCASE_SOURCE,
    flowGraph: MIXED_LANE_GRAPH as unknown as FlowGraph,
    isReadOnly: true,
    isHorizontal: true,
    execution: MIXED_LANE_EXECUTION,
    defaultViewport: {x: 0, y: 0, zoom: 1.5},
}

// Three independent canvases, the same flow, three different starting zooms — one below the pill
// threshold, one at rest, one above the expanded threshold. If a level of detail ever changed the
// laid-out footprint, the same node would measure differently across them; it must not
// (kestra-io/kestra#19666's hard constraint: crossing a zoom threshold moves no node).
export const FootprintInvariance: StoryObj<typeof Topology> = {
    render: () => ({
        components: {Topology},
        setup() {
            return {
                source: STATUS_SHOWCASE_SOURCE,
                flowGraph: STATUS_SHOWCASE_GRAPH as unknown as FlowGraph,
            }
        },
        template: `
            <div style="display: flex; gap: 8px;">
                <div id="footprint-pill" style="height: 420px; width: 420px;">
                    <Topology id="story-footprint-pill" :source="source" :flowGraph="flowGraph" isReadOnly :isHorizontal="true" :defaultViewport="{x: 0, y: 0, zoom: 0.3}" />
                </div>
                <div id="footprint-default" style="height: 420px; width: 420px;">
                    <Topology id="story-footprint-default" :source="source" :flowGraph="flowGraph" isReadOnly :isHorizontal="true" :defaultViewport="{x: 0, y: 0, zoom: 1}" />
                </div>
                <div id="footprint-expanded" style="height: 420px; width: 420px;">
                    <Topology id="story-footprint-expanded" :source="source" :flowGraph="flowGraph" isReadOnly :isHorizontal="true" :defaultViewport="{x: 0, y: 0, zoom: 1.6}" />
                </div>
            </div>
        `,
    }),
    play: async ({canvasElement}) => {
        const nodeAt = (containerId: string) =>
            canvasElement.querySelector(`#${containerId} [data-id="root.log_start"]`) as HTMLElement | null

        const pill = nodeAt("footprint-pill")
        const atRest = nodeAt("footprint-default")
        const expanded = nodeAt("footprint-expanded")

        await expect(pill).not.toBeNull()
        await expect(atRest).not.toBeNull()
        await expect(expanded).not.toBeNull()

        const dimensionsOf = (el: HTMLElement) => ({width: el.style.width, height: el.style.height})

        expect(dimensionsOf(pill!)).toEqual({width: "218px", height: "80px"})
        expect(dimensionsOf(atRest!)).toEqual(dimensionsOf(pill!))
        expect(dimensionsOf(expanded!)).toEqual(dimensionsOf(pill!))
    },
}
