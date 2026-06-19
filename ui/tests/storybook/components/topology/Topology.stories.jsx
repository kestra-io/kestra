import {Topology} from "@kestra-io/topology"
import allowFailureDemo from "../../../fixtures/flowgraphs/allow-failure-demo.json"
import eachSequential from "../../../fixtures/flowgraphs/each-sequential.json"

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
}

const Template = (args) => ({
    components: {Topology},
    setup() {
        return {args}
    },
    template: `<div style="height: 600px; width: 100%;"><Topology v-bind="args" /></div>`,
})

export const AllowFailure = Template.bind({})
AllowFailure.args = {
    id: "story-allow-failure",
    source: ALLOW_FAILURE_SOURCE,
    flowGraph: allowFailureDemo,
    isReadOnly: true,
    isHorizontal: true,
}

export const EachSequential = Template.bind({})
EachSequential.args = {
    id: "story-each-sequential",
    source: EACH_SEQUENTIAL_SOURCE,
    flowGraph: eachSequential,
    isReadOnly: true,
    isHorizontal: true,
}

export const Vertical = Template.bind({})
Vertical.args = {
    id: "story-vertical",
    source: ALLOW_FAILURE_SOURCE,
    flowGraph: allowFailureDemo,
    isReadOnly: true,
    isHorizontal: false,
}

export const StatusShowcase = Template.bind({})
StatusShowcase.storyName = "Status Showcase"
StatusShowcase.args = {
    id: "story-status-showcase",
    source: STATUS_SHOWCASE_SOURCE,
    flowGraph: STATUS_SHOWCASE_GRAPH,
    isReadOnly: true,
    isHorizontal: true,
}
