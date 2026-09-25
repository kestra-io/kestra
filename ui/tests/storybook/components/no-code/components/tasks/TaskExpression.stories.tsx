import {computed, provide, ref} from "vue";
import TaskExpression from "../../../../../../src/components/no-code/components/tasks/TaskExpression.vue";
import {Meta, StoryObj} from "@storybook/vue3-vite";
import {expect, userEvent, waitFor, within} from "storybook/test";
import {vueRouter} from "storybook-vue3-router";
import {FOCUSED_EXPRESSION_EDITOR_INJECTION_KEY, SCHEMA_DEFINITIONS_INJECTION_KEY} from "../../../../../../src/components/no-code/injectionKeys";

const meta: Meta<typeof TaskExpression> = {
    title: "Components/NoCode/TaskExpression",
    component: TaskExpression,
    decorators: [
        vueRouter([{path: "/", name: "home", component: {template: "<div>home</div>"}}]),
    ],
};

export default meta;

type Story = StoryObj<typeof TaskExpression>;

const render: Story["render"] = (args) => ({
    setup() {
        provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
        const model = ref(args.modelValue);
        return () => <div style={{display: "flex", gap: "16px"}}>
            <div style={{width: "500px"}}>
                <TaskExpression
                    modelValue={model.value}
                    onUpdate:modelValue={(val) => model.value = val}
                    root={args.root}
                />
            </div>
            <pre data-testid="result">{JSON.stringify(model.value, null, 2)}</pre>
        </div>
    },
});

export const Default: Story = {
    render,
    args: {
        modelValue: undefined,
        root: "expression",
    },
};

export const WithStringValue: Story = {
    render,
    args: {
        modelValue: "{{ outputs.myTask.uri }}",
        root: "expression",
    },
};

export const WithObjectValue: Story = {
    render,
    args: {
        modelValue: {key: "value", nested: {a: 1}},
        root: "config",
    },
};

export const RegistersFocusedExpressionEditor: Story = {
    render: (args) => ({
        setup() {
            provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
            const focusedExpressionEditorInsert = ref<((text: string) => void) | null>(null);
            provide(FOCUSED_EXPRESSION_EDITOR_INJECTION_KEY, focusedExpressionEditorInsert);
            const model = ref(args.modelValue);
            return () => <div style={{width: "500px"}}>
                <TaskExpression
                    modelValue={model.value}
                    onUpdate:modelValue={(val) => model.value = val}
                    root={args.root}
                />
                <span data-testid="focused-state">{focusedExpressionEditorInsert.value ? "registered" : "cleared"}</span>
            </div>
        },
    }),
    args: {
        modelValue: "{{ outputs.myTask.uri }}",
        root: "expression",
    },
    play: async ({canvasElement}) => {
        // Regression: clicking an Inputs-panel chip while this field was focused fell through
        // to clipboard copy, since Monaco fields are excluded from the plain-input "armed" tracking.
        const canvas = within(canvasElement);
        const editorContainer = await waitFor(() => canvas.getByTestId("monaco-editor"));
        const input = within(editorContainer).getByRole("textbox");

        await userEvent.click(input);
        await waitFor(() => expect(canvas.getByTestId("focused-state")).toHaveTextContent("registered"));

        await userEvent.click(document.body);
        await waitFor(() => expect(canvas.getByTestId("focused-state")).toHaveTextContent("cleared"));
    },
};
