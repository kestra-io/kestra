import {vi} from "vitest";

// playground store imports @kestra-io/topology (dagre + @vue-flow/core) at module level,
// which crashes the Chromium browser runner — mock it to avoid the import chain entirely.
vi.mock("../../../../../../src/stores/playground", () => ({
    usePlaygroundStore: () => ({enabled: false, runUntilTask: vi.fn()}),
}));

import {computed, provide, ref} from "vue";
import TaskList from "../../../../../../src/components/no-code/components/tasks/TaskList.vue";
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

const meta: Meta<typeof TaskList> = {
    title: "Components/NoCode/TaskList",
    component: TaskList,
    decorators: [
        vueRouter([{path: "/", name: "home", component: {template: "<div>home</div>"}}]),
    ],
};

export default meta;

type Story = StoryObj<typeof TaskList>;

const sampleSchema = {
    definitions: {
        "io.kestra.plugin.core.log.Log": {
            type: "object",
            properties: {
                id: {type: "string"},
                type: {const: "io.kestra.plugin.core.log.Log"},
                message: {type: "string"},
            },
        },
    },
    $ref: "#/definitions/Flow",
};

export const Default: Story = {
    render: (args) => ({
        setup() {
            provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => sampleSchema.definitions));
            provide(FULL_SCHEMA_INJECTION_KEY, ref(sampleSchema));
            provide(FULL_SOURCE_INJECTION_KEY, computed(() => ""));
            provide(PARENT_PATH_INJECTION_KEY, "");
            provide(BLOCK_SCHEMA_PATH_INJECTION_KEY, computed(() => ""));
            provide(UPDATE_YAML_FUNCTION_INJECTION_KEY, () => {});
            provide(CREATING_TASK_INJECTION_KEY, false);
            const model = ref(args.modelValue);
            return () => <div style={{width: "600px"}}>
                <TaskList
                    modelValue={model.value}
                    onUpdate:modelValue={(val) => model.value = val}
                    root="tasks"
                />
            </div>
        },
    }),
    args: {
        modelValue: [
            {id: "log1", type: "io.kestra.plugin.core.log.Log"},
            {id: "log2", type: "io.kestra.plugin.core.log.Log"},
        ],
    },
};

export const Merged: Story = {
    render: (args) => ({
        setup() {
            provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => sampleSchema.definitions));
            provide(FULL_SCHEMA_INJECTION_KEY, ref(sampleSchema));
            provide(FULL_SOURCE_INJECTION_KEY, computed(() => ""));
            provide(PARENT_PATH_INJECTION_KEY, "");
            provide(BLOCK_SCHEMA_PATH_INJECTION_KEY, computed(() => ""));
            provide(UPDATE_YAML_FUNCTION_INJECTION_KEY, () => {});
            provide(CREATING_TASK_INJECTION_KEY, false);
            const model = ref(args.modelValue);
            return () => <div style={{width: "600px"}}>
                <TaskList
                    modelValue={model.value}
                    onUpdate:modelValue={(val) => model.value = val}
                    merge
                />
            </div>
        },
    }),
    args: {
        modelValue: [
            {id: "task1", type: "io.kestra.plugin.core.log.Log"},
        ],
    },
};
