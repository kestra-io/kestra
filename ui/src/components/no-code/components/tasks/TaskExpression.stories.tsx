import {computed, provide, ref} from "vue";
import TaskExpression from "./TaskExpression.vue";
import {Meta, StoryObj} from "@storybook/vue3-vite";
import {expect, userEvent, waitFor, within} from "storybook/test";
import {vueRouter} from "storybook-vue3-router";
import {FOCUSED_EXPRESSION_EDITOR_INJECTION_KEY, SCHEMA_DEFINITIONS_INJECTION_KEY} from "../../injectionKeys";

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
            const mounted = ref(true);
            return () => <div style={{width: "500px"}}>
                {mounted.value && <TaskExpression
                    modelValue={model.value}
                    onUpdate:modelValue={(val) => model.value = val}
                    root={args.root}
                />}
                <button data-testid="unmount-field" onClick={() => mounted.value = false}>unmount</button>
                <span data-testid="focused-state">{focusedExpressionEditorInsert.value ? "registered" : "cleared"}</span>
            </div>
        },
    }),
    args: {
        modelValue: "{{ outputs.myTask.uri }}",
        root: "expression",
    },
    play: async ({canvasElement}) => {
        // Monaco runs inline editors in tab-focus mode, so Tab fires focusout while focus is still
        // inside the panel: the registration has to survive a blur and go only on unmount.
        const canvas = within(canvasElement);
        const editorContainer = await waitFor(() => canvas.getByTestId("monaco-editor"), {timeout: 15000});
        const input = within(editorContainer).getByRole("textbox");

        await userEvent.click(input);
        await waitFor(() => expect(canvas.getByTestId("focused-state")).toHaveTextContent("registered"));

        await userEvent.click(document.body);
        await waitFor(() => expect(canvas.getByTestId("focused-state")).toHaveTextContent("registered"));

        await userEvent.click(canvas.getByTestId("unmount-field"));
        await waitFor(() => expect(canvas.getByTestId("focused-state")).toHaveTextContent("cleared"));
    },
};
