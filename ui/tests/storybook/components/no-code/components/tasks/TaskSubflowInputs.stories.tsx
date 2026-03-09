import {computed, provide, ref} from "vue";
// @ts-expect-error Options API component without type declarations
import TaskSubflowInputs from "../../../../../../src/components/no-code/components/tasks/TaskSubflowInputs.vue";
import {Meta, StoryObj} from "@storybook/vue3-vite";
import {vueRouter} from "storybook-vue3-router";
import {SCHEMA_DEFINITIONS_INJECTION_KEY} from "../../../../../../src/components/no-code/injectionKeys";

const meta: Meta<typeof TaskSubflowInputs> = {
    title: "Components/NoCode/TaskSubflowInputs",
    component: TaskSubflowInputs,
    decorators: [
        vueRouter([{path: "/", name: "home", component: {template: "<div>home</div>"}}]),
    ],
};

export default meta;

type Story = StoryObj<typeof TaskSubflowInputs>;

export const WithTask: Story = {
    render: (args: any) => ({
        setup() {
            provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
            const model = ref(args.modelValue ?? {"":  undefined});
            return () => <div style={{display: "flex", gap: "16px"}}>
                <div style={{width: "600px"}}>
                    <TaskSubflowInputs
                        modelValue={model.value}
                        onUpdate:modelValue={(val: any) => model.value = val}
                        schema={{type: "object"}}
                        task={{namespace: "io.kestra.demo", flowId: "mySubflow"}}
                    />
                </div>
                <pre data-testid="result">{JSON.stringify(model.value, null, 2)}</pre>
            </div>
        },
    }),
    args: {
        modelValue: {"": undefined},
    },
};

export const WithoutFlowId: Story = {
    render: (args: any) => ({
        setup() {
            provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
            const model = ref(args.modelValue ?? {"":  undefined});
            return () => <div style={{display: "flex", gap: "16px"}}>
                <div style={{width: "600px"}}>
                    <TaskSubflowInputs
                        modelValue={model.value}
                        onUpdate:modelValue={(val: any) => model.value = val}
                        schema={{type: "object"}}
                        task={{namespace: "io.kestra.demo"}}
                    />
                </div>
                <pre data-testid="result">{JSON.stringify(model.value, null, 2)}</pre>
            </div>
        },
    }),
    args: {
        modelValue: {"": undefined},
    },
};
