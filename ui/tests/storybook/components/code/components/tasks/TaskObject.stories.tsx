import TaskObject from "../../../../../../src/components/code/components/tasks/TaskObject.vue";
import {ref} from "vue"
import {StoryObj} from "@storybook/vue3-vite";
import {waitFor, within, expect, fireEvent} from "storybook/test";
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

type Story = StoryObj<typeof TaskObject>;

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

const AppTableBlockRender = () => ({
    setup() {
        const model = ref<Record<string, any> | undefined>({})
        return () => <div style={{display: "flex", gap: "16px"}}>
            <div style={{width: "500px"}}>
                <TaskObject
                    schema={schema}
                    modelValue={model.value}
                    onUpdate:modelValue={(value) => model.value = value}
                />
            </div>
            <div style={{width: "500px"}}>
                <h2>Resulting object</h2>
                <pre style={{
                    border: "1px solid #555",
                    borderRadius: "4px",
                    padding: "2px",
                    background: "#222"
                }} data-testid="resulting-object">{JSON.stringify(model.value, null, 2)}</pre>
            </div>
        </div>
    }
});

export const AppTableBlock: Story = {
    render: AppTableBlockRender,
    async play({canvasElement}) {
        const canvas = within(canvasElement);
        canvas.getByText("+ Add a new value").click();
        await waitFor(() => {
            expect(canvas.getByText(/null/)).toBeVisible();
        });
        canvas.getByText("+ Add a new value", {selector: ".schema-wrapper .schema-wrapper button"}).click();

        await waitFor(() => {
            expect(canvas.getByPlaceholderText("Key")).toBeVisible();
        });

        fireEvent.input(canvas.getByPlaceholderText("Key"), {target: {value: "key1"}})
        fireEvent.input(canvas.getByTestId("monaco-editor-hidden-synced-textarea"), {target: {value: "value1"}})

        canvas.getByText("+ Add a new value", {selector: ".schema-wrapper .schema-wrapper button"}).click();

        await waitFor(() => {
            expect(canvas.getAllByPlaceholderText("Key")[1]).toBeVisible();
        });

        fireEvent.input(canvas.getAllByPlaceholderText("Key")[1], {target: {value: "key2"}})
        fireEvent.input(canvas.getAllByTestId("monaco-editor-hidden-synced-textarea")[1], {target: {value: "value2"}})

        await waitFor(() => {
            expect(canvas.getByTestId("resulting-object").innerHTML).toBe(JSON.stringify({
                data: [
                    {
                        key1: "value1",
                        key2: "value2"
                    }
                ]
            }, null, 2));
        });

    }
}
