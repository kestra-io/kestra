import {computed, provide, ref} from "vue";
import TaskNamespace from "../../../../../../src/components/no-code/components/tasks/TaskNamespace.vue";
import {Meta, StoryObj} from "@storybook/vue3-vite";
import {vueRouter} from "storybook-vue3-router";
import {
    CREATING_FLOW_INJECTION_KEY,
    DEFAULT_NAMESPACE_INJECTION_KEY,
} from "../../../../../../src/components/no-code/injectionKeys";

const meta: Meta<typeof TaskNamespace> = {
    title: "Components/NoCode/TaskNamespace",
    component: TaskNamespace,
    decorators: [
        vueRouter([{path: "/", name: "home", component: {template: "<div>home</div>"}}]),
    ],
};

export default meta;

type Story = StoryObj<typeof TaskNamespace>;

export const Creating: Story = {
    render: () => ({
        setup() {
            provide(CREATING_FLOW_INJECTION_KEY, true);
            provide(DEFAULT_NAMESPACE_INJECTION_KEY, computed(() => "io.kestra.demo"));
            const model = ref("");
            return () => <div style={{display: "flex", gap: "16px"}}>
                <div style={{width: "400px"}}>
                    <TaskNamespace
                        modelValue={model.value}
                        onUpdate:modelValue={(val: string | undefined) => { if (val) model.value = val }}
                    />
                </div>
                <pre data-testid="result">{JSON.stringify(model.value)}</pre>
            </div>
        },
    }),
};

export const Editing: Story = {
    render: () => ({
        setup() {
            provide(CREATING_FLOW_INJECTION_KEY, false);
            provide(DEFAULT_NAMESPACE_INJECTION_KEY, computed(() => "io.kestra.production"));
            const model = ref("io.kestra.production");
            return () => <div style={{display: "flex", gap: "16px"}}>
                <div style={{width: "400px"}}>
                    <TaskNamespace
                        modelValue={model.value}
                        onUpdate:modelValue={(val: string | undefined) => { if (val) model.value = val }}
                    />
                </div>
                <pre data-testid="result">{JSON.stringify(model.value)}</pre>
            </div>
        },
    }),
};
