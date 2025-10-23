import {vueRouter} from "storybook-vue3-router";
import MultiPanelFlowEditorView from "../../../../src/components/flows/MultiPanelFlowEditorView.vue";
import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
import allowFailureDemo from "../../../fixtures/flowgraphs/allow-failure-demo.json";
import flowSchema from "../../../../src/stores/flow-schema.json";
import {useAxios} from "../../../../src/utils/axios";
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
        const axios = useAxios()
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
            if (uri.endsWith("/subgroups")) {
                return {data: []}
            }
            console.log("get request", uri)
            return {data: {}}
        }
        axios.post = async (uri) => {
            if (uri.endsWith("/graph")) {
                return {data: allowFailureDemo}
            }
            if (uri.endsWith("/validate")) {
                return {data: {}}
            }
            console.log("post request", uri)
            return {data: {}}
        }

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
