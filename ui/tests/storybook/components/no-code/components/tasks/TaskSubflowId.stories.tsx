import {computed, provide, ref} from "vue";
import TaskSubflowId from "../../../../../../src/components/no-code/components/tasks/TaskSubflowId.vue";
import {Meta, StoryObj} from "@storybook/vue3-vite";
import {vueRouter} from "storybook-vue3-router";
import {SCHEMA_DEFINITIONS_INJECTION_KEY} from "../../../../../../src/components/no-code/injectionKeys";

const meta: Meta<typeof TaskSubflowId> = {
    title: "Components/NoCode/TaskSubflowId",
    component: TaskSubflowId,
    decorators: [
        vueRouter([{path: "/", name: "home", component: {template: "<div>home</div>"}}]),
    ],
};

export default meta;

type Story = StoryObj<typeof TaskSubflowId>;

export const Default: Story = {
    render: (args: any) => ({
        setup() {
            provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
            const model = ref(args.modelValue);
            return () => <div style={{display: "flex", gap: "16px"}}>
                <div style={{width: "400px"}}>
                    <TaskSubflowId
                        modelValue={model.value}
                        onUpdate:modelValue={(val: any) => model.value = val}
                        schema={{type: "string"}}
                        task={{namespace: "io.kestra.demo"}}
                    />
                </div>
                <pre data-testid="result">{JSON.stringify(model.value)}</pre>
            </div>
        },
    }),
    args: {
        modelValue: undefined,
    },
};

export const WithoutNamespace: Story = {
    render: (args: any) => ({
        setup() {
            provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
            const model = ref(args.modelValue);
            return () => <div style={{display: "flex", gap: "16px"}}>
                <div style={{width: "400px"}}>
                    <TaskSubflowId
                        modelValue={model.value}
                        onUpdate:modelValue={(val: any) => model.value = val}
                        schema={{type: "string"}}
                        task={{}}
                    />
                </div>
                <pre data-testid="result">{JSON.stringify(model.value)}</pre>
            </div>
        },
    }),
    args: {
        modelValue: undefined,
    },
};
