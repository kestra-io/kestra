import TaskObject from "../../../../../../src/components/code/components/tasks/TaskObject.vue";
import {ref} from "vue"
import {vueRouter} from "storybook-vue3-router";

export default {
    decorators: [vueRouter([
        {
            path: "/",
            name: "home",
            component: {template: "<div>home</div>"}
        }])
    ],
    title: "Components/NoCode/TaskObject",
    component: TaskObject,
}

const schema = {
  type: "object",
  properties: {
    data: {
      title: "The list of data rows for the table.",
      type: "array",
      items: {type: "object"},
    },
    type: {const: "io.kestra.plugin.ee.apps.core.blocks.Table"},
  },
  title: "A block for displaying a table.",
  required: ["id", "id"],
};

export const AppTableBlock = () => ({
    setup() {
        const model = ref({})
        return () => <div style={{display: "flex"}}>
            <div style={{width: "500px"}}>
                <TaskObject
                    schema={schema}
                    modelValue={model.value}
                    onUpdate:modelValue={(value) => model.value = value}
                />
            </div>
            <div style={{width: "500px"}}>
                <pre>{JSON.stringify(model.value, null, 2)}</pre>
            </div>
        </div>
    }
});