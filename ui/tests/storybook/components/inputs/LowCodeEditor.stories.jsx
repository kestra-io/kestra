import {provide, ref} from "vue";
import {TOPOLOGY_CLICK_INJECTION_KEY} from "../../../../src/components/no-code/injectionKeys";
import {vueRouter} from "storybook-vue3-router";
import LowCodeEditor from "../../../../src/components/inputs/LowCodeEditor.vue";
import {setMockClient} from "@kestra-io/kestra-sdk"
import {mockClientFallback} from "../../../../.storybook/apiMock";
import allowFailureDemo from "../../../fixtures/flowgraphs/allow-failure-demo.json";

export default {
    title: "Components/Inputs/LowCodeEditor",
    component: LowCodeEditor,
    decorators: [vueRouter([
            {
                path: "/",
                name: "home",
                component: {template: "<div>home</div>"}
            },
        ])]
};

const Template = (args) => ({
    setup() {
        const axios = {}
        provide(TOPOLOGY_CLICK_INJECTION_KEY, ref())
        axios.get = async (uri) => mockClientFallback("GET", uri)
        setMockClient(axios);

        return () => (<div style="width:600px; height:600px;">
            <LowCodeEditor {...args} />
        </div>);
    }
});

export const Default = Template.bind({});
Default.args = {
    flowGraph: allowFailureDemo,
    flowId: "allow-failure-demo",
    namespace: "sanitychecks.flows.blueprints",
    execution: {},
    isReadOnly: false,
    // Must stay flush against column 0 and match the graph fixture above: flowHaveTasks()
    // (packages/topology/src/utils/vueFlowUtils.ts) matches /^tasks\s*:/m, so an indented source
    // reads as a flow with no tasks and the topology renders its empty placeholder instead.
    source: `
id: allow-failure-demo
namespace: sanitychecks.flows.blueprints
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
`.trim(),
    isAllowedEdit: true,
    viewType: "default",
    expandedSubflows: [],
};
