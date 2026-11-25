import {vueRouter} from "storybook-vue3-router";
import {useRouter} from "vue-router";
import {expect, waitFor, within} from "storybook/test";
import MultiPanelFlowEditorView from "../../../../src/components/flows/MultiPanelFlowEditorView.vue";
import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils";
import allowFailureDemo from "../../../fixtures/flowgraphs/allow-failure-demo.json";
import flowSchema from "../../../../src/stores/flow-schema.json";
import {setMockClient} from "@kestra-io/kestra-sdk"
import {mockClientFallback} from "../../../../.storybook/apiMock";
import {useFlowStore} from "../../../../src/stores/flow";


export default {
    title: "Components/MultiPanelFlowEditorView",
    component: MultiPanelFlowEditorView,
    decorators: [
        vueRouter([
            {
                path: "/",
                name: "home",
                component: {template: "<div>home</div>"}
            },
            {
                path: "/flows/edit/:namespace/",
                name: "flows/edit",
                component: {template: "<div>update flows</div>"}
            }
        ])
    ]
};

const Template = (args) => ({
    setup() {
        const axios = {}
        const flowStore = useFlowStore()
        axios.get = async (uri) => {
            if (uri.endsWith("/plugins")) {
                return {data: []}
            }
            if (uri.endsWith("/flow")) {
                return {data: flowSchema}
            }
            if (uri.endsWith("/distinct-namespaces")) {
                return {data: ["sanitychecks.flows.blueprints", "tutorial"]}
            }
            // Anything this story doesn't answer itself falls back to the shared table in
            // .storybook/apiMock.js, which reports the route if nothing there covers it either.
            return mockClientFallback("GET", uri)
        }
        axios.post = async (uri, data) => {
            if (uri.endsWith("/graph")) {
                return {data: allowFailureDemo}
            }
            if (uri.endsWith("/validate")) {
                return {data: {}}
            }
            return mockClientFallback("POST", uri, data)
        }
        setMockClient(axios);

        const flow = YAML_UTILS.parse(args.flow)
        flow.source = args.flow
        flowStore.flow = flow
        flowStore.flowYaml = args.flow

        return () =>
            <div style="height: 100vh">
                <MultiPanelFlowEditorView/>
            </div>
    }
});

export const Default = Template.bind({});
Default.args = {
    flow: `
id: allow-failure-demo
namespace: sanitychecks.flows.blueprints
tasks:
  - id: allow_failure
    type: io.kestra.plugin.core.flow.AllowFailure
    tasks:
      - id: fail_silently
        type: io.kestra.plugin.scripts.shell.Commands
        taskRunner:
          type: io.kestra.plugin.core.runner.Process
        commands:
          - exit 1
  - id: print_to_console
    type: io.kestra.plugin.scripts.shell.Commands
    taskRunner:
      type: io.kestra.plugin.core.runner.Process
    commands:
      - echo "this will run since previous failure was allowed ✅"
`.trim(),
};

export const EmptyFlow = Template.bind({});
EmptyFlow.args = {
    flow: `
id: empty
namespace: sanitychecks.flows.blueprints
`.trim(),
};

export const ComplexFlow = Template.bind({});
ComplexFlow.args = {
    flow: `
id: hello-world
namespace: tutorial
description: Hello World

inputs:
  - id: user
    type: STRING
    defaults: Rick Astley

tasks:
  - id: first_task
    type: io.kestra.plugin.core.debug.Return
    format: thrilled

  - id: second_task
    type: io.kestra.plugin.scripts.shell.Commands
    commands:
      - sleep 0.42
      - echo '::{"outputs":{"returned_data":"mydata"}}::'

  - id: hello_world
    type: io.kestra.plugin.core.log.Log
    message: |
      Welcome to Kestra, {{ inputs.user }}!
      We are {{ outputs.first_task.value}} to have You here!

triggers:
  - id: daily
    type: io.kestra.plugin.core.trigger.Schedule
    disabled: true
    cron: 0 9 * * *
`.trim(),
};

const FILE_TAB_FLOW_A = "id: flow_a\nnamespace: repro.a\ntasks: []"
const FILE_TAB_FLOW_B = "id: flow_b\nnamespace: repro.b\ntasks: []"
let fileTabRouter

export const FileTabsStayWithTheirFlow = Template.bind({});
FileTabsStayWithTheirFlow.args = {flow: FILE_TAB_FLOW_A};
FileTabsStayWithTheirFlow.decorators = [() => ({
    setup() {
        localStorage.setItem("el-fl-repro.a-flow_a", JSON.stringify([{tabs: ["code", "code-conf.yml"], activeTab: "code", size: 100}]))
        fileTabRouter = useRouter()
        fileTabRouter.addRoute({path: "/flows/edit/:namespace/:id/edit", name: "flows/update/edit", component: {template: "<div/>"}})
    },
    template: "<story />",
})];
FileTabsStayWithTheirFlow.play = async ({canvasElement}) => {
    const canvas = within(canvasElement)
    await fileTabRouter.push("/flows/edit/repro.a/flow_a/edit")
    await canvas.findByText("conf.yml")

    const flowStore = useFlowStore()
    flowStore.flow = {...YAML_UTILS.parse(FILE_TAB_FLOW_B), source: FILE_TAB_FLOW_B}
    flowStore.flowYaml = FILE_TAB_FLOW_B
    await fileTabRouter.push("/flows/edit/repro.b/flow_b/edit")

    await waitFor(() => expect(canvas.queryByText("conf.yml")).toBeNull())
};
