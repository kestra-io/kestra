import Editor from "../../../../../src/components/code/segments/Editor.vue";
import InitialSchema from "../../../../../src/components/code/segments/flow-schema.json";
import {
    CREATING_TASK_INJECTION_KEY, FLOW_INJECTION_KEY,
    POSITION_INJECTION_KEY,
    BLOCKTYPE_INJECT_KEY, REF_PATH_INJECTION_KEY,
    PARENT_PATH_INJECTION_KEY,
    EDITING_TASK_INJECTION_KEY
} from "../../../../../src/components/code/injectionKeys";
import {provide, ref} from "vue";
import {useStore} from "vuex";
import {vueRouter} from "storybook-vue3-router";


export default {
    decorators: [vueRouter([
        {
            path: "/",
            name: "home",
            component: {template: "<div>home</div>"}
        }])
    ],
    title: "Components/NoCode/Editor",
    component: Editor,
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

const TASK_RESPONSE = {
    "markdown": `
Return a value for debugging purposes.

This task is intended for troubleshooting.

It allows you to return templated values, inputs or outputs.`.trim(),
    "schema": {
        "properties": {
            "properties": {
                "message": {
                    "type": "string",
                    "title": "The templated string to render.",
                    "$dynamic": true,
                    "$required": true
                },
                "values": {
                    "type": "array",
                    "title": "An array of value to test forms with it",
                    "items": {
                        "type": "string",
                        "title": "The templated value to return.",
                        "$dynamic": true,
                    },
                    "$dynamic": true,
                    "$required": true
                }
            },
            "title": "Return a value for debugging purposes.",
            "$examples": [],
            "$metrics": [],
            "required": ["message", "values"],
        },
        "outputs": {
            "properties": {
                "value": {
                    "type": "string",
                    "title": "The generated string.",
                    "$required": false
                }
            }
        },
        "definitions": {}
    }
}

const Template = (args) => ({
    setup() {
        const store = useStore()

        provide(FLOW_INJECTION_KEY, ref(args.flow));
        provide(BLOCKTYPE_INJECT_KEY, "tasks");
        provide(PARENT_PATH_INJECTION_KEY, "tasks");
        provide(REF_PATH_INJECTION_KEY, 0);
        provide(BLOCKTYPE_INJECT_KEY, "tasks");
        provide(POSITION_INJECTION_KEY, args.position);
        provide(CREATING_TASK_INJECTION_KEY, args.creating);
        provide(EDITING_TASK_INJECTION_KEY, args.editing);

        store.$http = {
            get(url) {
                if (url.endsWith("plugins")) {
                    return Promise.resolve({
                        data: PLUGINS_RESPONSE
                    })
                }
                if (
                    url.endsWith("debug.Return") || url.endsWith("debug.Echo")
                    || url.endsWith("http.Trigger") || url.endsWith("trigger.Flow")
                    || url.endsWith("condition.DateTimeBetween") || url.endsWith("condition.DayWeek")
                ) {
                    return Promise.resolve({
                        data: TASK_RESPONSE
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
            },
            post(url){
                if(url.endsWith("flows/validate/task")){
                    return Promise.resolve({data: {}})
                }
                return Promise.resolve({
                    data: []
                })
            }
        }
        return () =>
            <div style="margin: 1rem; width: 400px;border: 1px solid lightgray; padding: .5rem;">
                <Editor {...args.props}/>
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
    props:{
        metadata: {
            id: "example-id",
            namespace: "example-namespace",
        },
    },
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
    editing: true,
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
    props:{
        metadata: {
            id: "example-id",
            namespace: "example-namespace",
        },
    },
};

