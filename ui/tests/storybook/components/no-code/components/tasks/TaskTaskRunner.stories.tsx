import {computed, provide, ref} from "vue";
// @ts-expect-error Options API component without type declarations
import TaskTaskRunner from "../../../../../../src/components/no-code/components/tasks/TaskTaskRunner.vue";
import {Meta, StoryObj} from "@storybook/vue3-vite";
import {vueRouter} from "storybook-vue3-router";
import {
    SCHEMA_DEFINITIONS_INJECTION_KEY,
    FULL_SCHEMA_INJECTION_KEY,
    FULL_SOURCE_INJECTION_KEY,
    PARENT_PATH_INJECTION_KEY,
    BLOCK_SCHEMA_PATH_INJECTION_KEY,
    UPDATE_YAML_FUNCTION_INJECTION_KEY,
    CREATING_TASK_INJECTION_KEY,
} from "../../../../../../src/components/no-code/injectionKeys";

const meta: Meta<typeof TaskTaskRunner> = {
    title: "Components/NoCode/TaskTaskRunner",
    component: TaskTaskRunner,
    decorators: [
        vueRouter([{path: "/", name: "home", component: {template: "<div>home</div>"}}]),
    ],
};

export default meta;

type Story = StoryObj<typeof TaskTaskRunner>;

export const Default: Story = {
    render: (args: any) => ({
        setup() {
            const sampleSchema = {definitions: {}};
            provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
            provide(FULL_SCHEMA_INJECTION_KEY, ref({...sampleSchema, $ref: ""}));
            provide(FULL_SOURCE_INJECTION_KEY, computed(() => ""));
            provide(PARENT_PATH_INJECTION_KEY, "");
            provide(BLOCK_SCHEMA_PATH_INJECTION_KEY, computed(() => ""));
            provide(UPDATE_YAML_FUNCTION_INJECTION_KEY, () => {});
            provide(CREATING_TASK_INJECTION_KEY, false);
            const model = ref(args.modelValue ?? {} as any);
            return () => <div style={{width: "600px"}}>
                <TaskTaskRunner
                    modelValue={model.value}
                    onUpdate:modelValue={(val: any) => model.value = val}
                />
            </div>
        },
    }),
    args: {
        modelValue: {
            id: "runner",
            type: "io.kestra.plugin.core.runner.Process",
        },
    },
};
