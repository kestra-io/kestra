import {computed, provide, ref} from "vue";
import TaskExpression from "../../../../../../src/components/no-code/components/tasks/TaskExpression.vue";
import {Meta, StoryObj} from "@storybook/vue3-vite";
import {vueRouter} from "storybook-vue3-router";
import {SCHEMA_DEFINITIONS_INJECTION_KEY} from "../../../../../../src/components/no-code/injectionKeys";

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
