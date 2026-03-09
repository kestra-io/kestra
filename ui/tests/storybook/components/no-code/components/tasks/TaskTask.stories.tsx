import {computed, provide, ref} from "vue";
import TaskTask from "../../../../../../src/components/no-code/components/tasks/TaskTask.vue";
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

const meta: Meta<typeof TaskTask> = {
    title: "Components/NoCode/TaskTask",
    component: TaskTask,
    decorators: [
        vueRouter([{path: "/", name: "home", component: {template: "<div>home</div>"}}]),
    ],
};

export default meta;

type Story = StoryObj<typeof TaskTask>;

export const Default: Story = {
    render: (args: any) => ({
        setup() {
            const sampleSchema = {definitions: {}, $ref: ""};
            provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
            provide(FULL_SCHEMA_INJECTION_KEY, ref(sampleSchema));
            provide(FULL_SOURCE_INJECTION_KEY, computed(() => ""));
            provide(PARENT_PATH_INJECTION_KEY, "");
            provide(BLOCK_SCHEMA_PATH_INJECTION_KEY, computed(() => ""));
            provide(UPDATE_YAML_FUNCTION_INJECTION_KEY, () => {});
            provide(CREATING_TASK_INJECTION_KEY, false);
            const model = ref(args.modelValue ?? {});
            return () => <div style={{width: "600px"}}>
                <TaskTask
                    modelValue={model.value}
                    onUpdate:modelValue={(val: any) => model.value = val}
                    root="task"
                />
            </div>
        },
    }),
    args: {
        modelValue: {
            id: "myTask",
            type: "io.kestra.plugin.core.log.Log",
        },
    },
};

export const EmptyTask: Story = {
    render: (args: any) => ({
        setup() {
            const sampleSchema = {definitions: {}, $ref: ""};
            provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
            provide(FULL_SCHEMA_INJECTION_KEY, ref(sampleSchema));
            provide(FULL_SOURCE_INJECTION_KEY, computed(() => ""));
            provide(PARENT_PATH_INJECTION_KEY, "");
            provide(BLOCK_SCHEMA_PATH_INJECTION_KEY, computed(() => ""));
            provide(UPDATE_YAML_FUNCTION_INJECTION_KEY, () => {});
            provide(CREATING_TASK_INJECTION_KEY, false);
            const model = ref(args.modelValue ?? {});
            return () => <div style={{width: "600px"}}>
                <TaskTask
                    modelValue={model.value}
                    onUpdate:modelValue={(val: any) => model.value = val}
                    root="task"
                />
            </div>
        },
    }),
    args: {
        modelValue: {},
    },
};
