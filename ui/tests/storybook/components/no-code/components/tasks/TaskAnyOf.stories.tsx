import {computed, provide, ref} from "vue";
import TaskAnyOf from "../../../../../../src/components/no-code/components/tasks/TaskAnyOf.vue";
import {Meta, StoryObj} from "@storybook/vue3-vite";
import {vueRouter} from "storybook-vue3-router";
import {SCHEMA_DEFINITIONS_INJECTION_KEY} from "../../../../../../src/components/no-code/injectionKeys";

const meta: Meta<typeof TaskAnyOf> = {
    title: "Components/NoCode/TaskAnyOf",
    component: TaskAnyOf,
    decorators: [
        vueRouter([{path: "/", name: "home", component: {template: "<div>home</div>"}}]),
    ],
};

export default meta;

type Story = StoryObj<typeof TaskAnyOf>;

export const SimpleTypes: Story = {
    render: (args) => ({
        setup() {
            provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
            const model = ref(args.modelValue);
            return () => <div style={{display: "flex", gap: "16px"}}>
                <div style={{width: "500px"}}>
                    <TaskAnyOf
                        modelValue={model.value}
                        onUpdate:modelValue={(val) => model.value = val}
                        schema={args.schema}
                    />
                </div>
                <pre data-testid="result">{JSON.stringify(model.value, null, 2)}</pre>
            </div>
        },
    }),
    args: {
        modelValue: undefined,
        schema: {
            anyOf: [
                {type: "string"} as any,
                {type: "number"} as any,
                {type: "boolean"} as any,
            ],
        } as any,
    },
};

export const ArrayVariants: Story = {
    render: (args) => ({
        setup() {
            provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
            const model = ref(args.modelValue);
            return () => <div style={{display: "flex", gap: "16px"}}>
                <div style={{width: "500px"}}>
                    <TaskAnyOf
                        modelValue={model.value}
                        onUpdate:modelValue={(val) => model.value = val}
                        schema={args.schema}
                    />
                </div>
                <pre data-testid="result">{JSON.stringify(model.value, null, 2)}</pre>
            </div>
        },
    }),
    args: {
        modelValue: undefined,
        schema: {
            anyOf: [
                {type: "array", items: {type: "string"}} as any,
                {type: "array", items: {type: "number"}} as any,
            ],
        } as any,
    },
};
