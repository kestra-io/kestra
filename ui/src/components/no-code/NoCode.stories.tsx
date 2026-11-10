import type {Meta, StoryFn} from "@storybook/vue3-vite";
import NoCode from "./NoCode.vue";
import InitialSchema from "../../stores/flow-schema.json";
import {vueRouter} from "storybook-vue3-router";
import {useFlowStore} from "../../stores/flow";
import {setMockClient, type AxiosLikeClient} from "@kestra-io/kestra-sdk"
import {mockResponse} from "../../../.storybook/apiMock"


export default {
    decorators: [vueRouter([
        {
            path: "/",
            name: "home",
            component: {template: "<div>home</div>"}
        }])
    ],
    title: "Components/NoCode/Editor",
    component: NoCode,
} as Meta<typeof NoCode>

const PLUGINS_RESPONSE = [{
    "name": "core",
    "title": "core",
    "group": "io.kestra.plugin.core",
    "manifest": {
        "X-Kestra-Title": "core",
        "X-Kestra-Group": "io.kestra.plugin.core",
        "Manifest-Version": "1.0"
    },
    "tasks": [
        "io.kestra.plugin.core.debug.Echo",
        "io.kestra.plugin.core.debug.Return",
    ],
    "triggers": [
        "io.kestra.plugin.core.http.Trigger",
        "io.kestra.plugin.core.trigger.Flow",
    ],
    "conditions": [
        "io.kestra.plugin.core.condition.DateTimeBetween",
        "io.kestra.plugin.core.condition.DayWeek",
    ]
}]

const Template: StoryFn<{flow: string; props?: Partial<InstanceType<typeof NoCode>["$props"]>}> = (args) => ({
    setup() {
        const flowStore = useFlowStore()
        const axios: Partial<AxiosLikeClient> = {}

        flowStore.flowYaml = args.flow
        const props = {
            parentPath: "tasks",
            refPath: 0,
            ...args.props
        }

        axios.get = async <T,>(url: string) => {
                if (url.endsWith("plugins")) {
                    return mockResponse<T>(PLUGINS_RESPONSE)
                }
                if (url.endsWith("/flow")) {
                    return mockResponse<T>(InitialSchema)
                }
                return mockResponse<T>([])
            }

        axios.post = async <T,>(url: string) => {
                if(url.endsWith("flows/validate/task")){
                    return mockResponse<T>({})
                }
                return mockResponse<T>([])
            }

            setMockClient(axios);

        return () =>
            <div style="margin: 1rem; width: 400px;border: 1px solid lightgray; padding: .5rem;">
                <NoCode {...props}/>
            </div>
    }
});

export const Default = Template.bind({});
Default.args = {
    flow: `
id: flow1
namespace: namespace1
tasks:
  - id: task1
    type: io.kestra.plugin.core.debug.Return
    message: "Hello world"
    values:
      - one
      - two
      - three
    `.trim(),
};

export const EditTask = Template.bind({});
EditTask.decorators = [vueRouter([
    {
        path: "/",
        name: "home",
        component: {template: "<div>home</div>"}
    },
    {
        path: "/flows",
        name: "flows",
        component: {template: "<div>flows</div>"}
    }])
]
EditTask.args = {
    flow: `
id: flow1
namespace: namespace1
tasks:
  - id: task1
    type: io.kestra.plugin.core.log.Log
    message: "Hello world"
    values:
      - one
      - two
      - three
    `.trim(),
    props: {
       editingTask: true,
       blockSchemaPath: "#/definitions/io.kestra.core.models.flows.Flow/properties/tasks/items",
    },
};
