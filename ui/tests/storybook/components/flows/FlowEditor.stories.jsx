import {useStore} from "vuex";
import {vueRouter} from "storybook-vue3-router";
import FlowEditor from "../../../../src/components/flows/FlowEditor.vue";
import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";
import allowFailureDemo from "../../../fixtures/flowgraphs/allow-failure-demo.json";


export default {
  title: "Components/FlowEditor",
  component: FlowEditor,
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
            component: {template: "<div>updateflows</div>"}
        }
    ])
  ]
};

const Template = (args) => ({
  setup() {
    const store = useStore()
    store.$http = {
        get: async (uri) => {
            console.log("get request", uri)
            if(uri.endsWith("/plugins")) {
                return {data: []}
            }
            return {data: {}}
        },
        post: async (uri) => {
            console.log("post request", uri)
            if(uri.endsWith("/graph")) {
                return {data: allowFailureDemo}
            }
            return {data: {}}
        }
    }
    const flow = YAML_UTILS.parse(args.flow)
    flow.source = args.flow
    store.commit("flow/setFlow", flow)
    store.dispatch("editor/openTab", {
        flow: true,
        name: "Flow",
        path: "Flow.yaml",
        persistent: true,
    })

    return () =>
        <div style="height: 100vh">
            <FlowEditor />
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
