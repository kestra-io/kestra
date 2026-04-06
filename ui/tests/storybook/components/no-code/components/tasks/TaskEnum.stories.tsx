import {computed, provide, ref} from "vue";
// @ts-expect-error Options API component without type declarations
import TaskEnum from "../../../../../../src/components/no-code/components/tasks/TaskEnum.vue";
import {Meta, StoryObj} from "@storybook/vue3-vite";
import {vueRouter} from "storybook-vue3-router";
import {SCHEMA_DEFINITIONS_INJECTION_KEY} from "../../../../../../src/components/no-code/injectionKeys";

const meta: Meta<typeof TaskEnum> = {
    title: "Components/NoCode/TaskEnum",
    component: TaskEnum,
    decorators: [
        vueRouter([{path: "/", name: "home", component: {template: "<div>home</div>"}}]),
    ],
};

export default meta;

type Story = StoryObj<typeof TaskEnum>;

const render: Story["render"] = (args: any) => ({
    setup() {
        provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
        const model = ref(args.modelValue);
        return () => <div style={{display: "flex", gap: "16px"}}>
            <div style={{width: "400px"}}>
                <TaskEnum
                    modelValue={model.value}
                    onUpdate:modelValue={(val: any) => model.value = val}
                    schema={args.schema}
                    root={args.root}
                />
            </div>
            <pre data-testid="result">{JSON.stringify(model.value)}</pre>
        </div>
    },
});

export const Default: Story = {
    render,
    args: {
        modelValue: undefined,
        schema: {
            type: "string",
            enum: ["DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"],
        },
        root: "level",
    },
};

export const WithSelection: Story = {
    render,
    args: {
        modelValue: "INFO",
        schema: {
            type: "string",
            enum: ["DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"],
        },
        root: "level",
    },
};

export const BooleanEnum: Story = {
    render,
    args: {
        modelValue: undefined,
        schema: {
            type: "string",
            enum: ["true", "false"],
        },
        root: "enabled",
    },
};
