import {ref} from "vue";
import TaskVersion from "../../../../../../src/components/no-code/components/tasks/TaskVersion.vue";
import {Meta, StoryObj} from "@storybook/vue3-vite";
import {vueRouter} from "storybook-vue3-router";

const meta: Meta<typeof TaskVersion> = {
    title: "Components/NoCode/TaskVersion",
    component: TaskVersion,
    decorators: [
        vueRouter([{path: "/", name: "home", component: {template: "<div>home</div>"}}]),
    ],
};

export default meta;

type Story = StoryObj<typeof TaskVersion>;

export const Default: Story = {
    render: (args) => ({
        setup() {
            const model = ref(args.modelValue ?? "");
            return () => <div style={{display: "flex", gap: "16px"}}>
                <div style={{width: "400px"}}>
                    <TaskVersion
                        modelValue={model.value}
                        onUpdate:modelValue={(val) => model.value = val}
                    />
                </div>
                <pre data-testid="result">{JSON.stringify(model.value)}</pre>
            </div>
        },
    }),
    args: {
        modelValue: "",
    },
};

export const WithVersion: Story = {
    render: (args) => ({
        setup() {
            const model = ref(args.modelValue ?? "");
            return () => <div style={{display: "flex", gap: "16px"}}>
                <div style={{width: "400px"}}>
                    <TaskVersion
                        modelValue={model.value}
                        onUpdate:modelValue={(val) => model.value = val}
                    />
                </div>
                <pre data-testid="result">{JSON.stringify(model.value)}</pre>
            </div>
        },
    }),
    args: {
        modelValue: "1.2.3",
    },
};
