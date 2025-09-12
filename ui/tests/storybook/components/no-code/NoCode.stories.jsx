import NoCode from "../../../../src/components/no-code/NoCode.vue";
import InitialSchema from "../../../../src/stores/flow-schema.json";
import {vueRouter} from "storybook-vue3-router";
import {useFlowStore} from "../../../../src/stores/flow";
import {useAxios} from "../../../../src/utils/axios";


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
}

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

const Template = (args) => ({
    setup() {
        const flowStore = useFlowStore()
        const axios = useAxios()

        flowStore.flowYaml = args.flow
        const props = {
            parentPath: "tasks",
            refPath: 0,
            ...args.props
        }

        axios.get = (url) => {
                if (url.endsWith("plugins")) {
                    return Promise.resolve({
                        data: PLUGINS_RESPONSE
                    })
                }
                if (url.endsWith("/flow")) {
                    return Promise.resolve({
                        data: InitialSchema
                    })
                }
                return Promise.resolve({
                    data: []
                })
            }

        axios.post = (url) => {
                if(url.endsWith("flows/validate/task")){
                    return Promise.resolve({data: {}})
                }
                return Promise.resolve({
                    data: []
                })
            }

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

