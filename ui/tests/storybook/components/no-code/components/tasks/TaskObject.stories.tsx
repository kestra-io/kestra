import TaskObject from "../../../../../../src/components/no-code/components/tasks/TaskObject.vue";
import {computed, provide, ref} from "vue"
import {StoryObj} from "@storybook/vue3-vite";
import {waitFor, within, expect, fireEvent} from "storybook/test";
import {vueRouter} from "storybook-vue3-router";
import {SCHEMA_DEFINITIONS_INJECTION_KEY} from "../../../../../../src/components/no-code/injectionKeys";

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
        provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
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
                    border: "1px solid var(--ks-border-primary)",
                    borderRadius: "4px",
                    padding: "2px",
                    background: "var(--ks-background-card)"
                }} data-testid="resulting-object">{JSON.stringify(model.value, null, 2)}</pre>
            </div>
        </div>
    }
});

export const AppTableBlock: Story = {
    render: AppTableBlockRender,
    async play({canvasElement}) {
        const canvas = within(canvasElement);
        fireEvent.click(await canvas.findByText("+ Add a new value"));
        expect(await canvas.findByText(/null/, {selector: "pre"})).toBeVisible();
        fireEvent.click(await canvas.findByText("+ Add a new value", {selector: ".schema-wrapper .schema-wrapper button"}));

        fireEvent.input(await canvas.findByPlaceholderText("Key"), {target: {value: "key1"}})
        fireEvent.input(await canvas.findByTestId("monaco-editor-hidden-synced-textarea"), {target: {value: "value1"}})

        fireEvent.click(await canvas.findByText("+ Add a new value", {selector: ".schema-wrapper .schema-wrapper button"}))

        await waitFor(function getByPlaceholderKey() {
            expect(canvas.getAllByPlaceholderText("Key")[1]).toBeVisible();
        });

        fireEvent.input((await canvas.findAllByPlaceholderText("Key"))[1], {target: {value: "key2"}})
        fireEvent.input((await canvas.findAllByTestId("monaco-editor-hidden-synced-textarea"))[1], {target: {value: "value2"}})

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
