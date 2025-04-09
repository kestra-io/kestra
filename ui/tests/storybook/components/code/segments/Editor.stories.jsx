import Editor from "../../../../../src/components/code/segments/Editor.vue";
import {ref} from "vue";
import {useStore} from "vuex";
import {vueRouter} from "storybook-vue3-router";

export default {
    decorators: [vueRouter([
        {
            path: "/",
            name: "home",
            component: {template: "div>home</div>"}
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
                "foo": {
                    "type": "string",
                    "title": "The templated string to render.",
                    "$dynamic": true,
                    "$required": true
                }
            },
            "title": "Return a value for debugging purposes.",
            "$examples": [],
            "$metrics": [],
            "required": ["foo"],
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
        const modelValue = ref(args.flow)

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
                return Promise.resolve({
                    data: []
                })
            }
        }
        return () =>
            <div style="margin: 1rem; width: 400px;border: 1px solid lightgray; padding: .5rem;">
                <Editor {...args} flow={modelValue.value} on />
            </div>
    }
});

export const Default = Template.bind({});
Default.args = {
    creation: false,
    flow: `
id: flow1
namespace: namespace1
tasks:
  - id: task1
    type: io.kestra.plugin.core.debug.Return
    `.trim(),
    metadata: {
        id: "example-id",
        namespace: "example-namespace",
        description: "Example description",
        retry: "",
        labels: {},
        inputs: [],
        outputs: "",
        variables: {},
        concurrency: {},
        pluginDefaults: "",
        disabled: false,
    },
};
