import {computed, provide, ref} from "vue";
import TaskAnyOfVue from "../../../../../../src/components/no-code/components/tasks/TaskAnyOf.vue";
// vue-tsgo (TypeScript 7) does not surface defineModel()-derived props on the
// component type when it is consumed from TSX, so type the component loosely here.
const TaskAnyOf = TaskAnyOfVue as unknown as import("vue").FunctionalComponent<Record<string, any>>;
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
                        onUpdate:modelValue={(val: any) => model.value = val}
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
                        onUpdate:modelValue={(val: any) => model.value = val}
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
