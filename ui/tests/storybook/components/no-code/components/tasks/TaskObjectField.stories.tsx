import {computed, provide, ref} from "vue";
import TaskObjectField from "../../../../../../src/components/no-code/components/tasks/TaskObjectField.vue";
import {Meta, StoryObj} from "@storybook/vue3-vite";
import {vueRouter} from "storybook-vue3-router";
import {SCHEMA_DEFINITIONS_INJECTION_KEY} from "../../../../../../src/components/no-code/injectionKeys";

const meta: Meta<typeof TaskObjectField> = {
    title: "Components/NoCode/TaskObjectField",
    component: TaskObjectField,
    decorators: [
        vueRouter([{path: "/", name: "home", component: {template: "<div>home</div>"}}]),
    ],
};

export default meta;

type Story = StoryObj<typeof TaskObjectField>;

export const StringField: Story = {
    render: (args) => ({
        setup() {
            provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
            const model = ref(args.modelValue);
            return () => <div style={{display: "flex", gap: "16px"}}>
                <div style={{width: "500px"}}>
                    <TaskObjectField
                        modelValue={model.value}
                        onUpdate:modelValue={(val) => model.value = val}
                        schema={{type: "string", title: "Description"}}
                        fieldKey="description"
                        task={{}}
                    />
                </div>
                <pre data-testid="result">{JSON.stringify(model.value)}</pre>
            </div>
        },
    }),
    args: {modelValue: "Hello"},
};

export const NumberField: Story = {
    render: (args) => ({
        setup() {
            provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
            const model = ref(args.modelValue);
            return () => <div style={{display: "flex", gap: "16px"}}>
                <div style={{width: "500px"}}>
                    <TaskObjectField
                        modelValue={model.value}
                        onUpdate:modelValue={(val) => model.value = val}
                        schema={{type: "number", title: "Retry count"}}
                        fieldKey="retry"
                        task={{}}
                    />
                </div>
                <pre data-testid="result">{JSON.stringify(model.value)}</pre>
            </div>
        },
    }),
    args: {modelValue: 3},
};

export const BooleanField: Story = {
    render: (args) => ({
        setup() {
            provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
            const model = ref(args.modelValue);
            return () => <div style={{display: "flex", gap: "16px"}}>
                <div style={{width: "500px"}}>
                    <TaskObjectField
                        modelValue={model.value}
                        onUpdate:modelValue={(val) => model.value = val}
                        schema={{type: "boolean", title: "Whether to wait"}}
                        fieldKey="wait"
                        task={{}}
                    />
                </div>
                <pre data-testid="result">{JSON.stringify(model.value)}</pre>
            </div>
        },
    }),
    args: {modelValue: true},
};

export const EnumField: Story = {
    render: (args) => ({
        setup() {
            provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
            const model = ref(args.modelValue);
            return () => <div style={{display: "flex", gap: "16px"}}>
                <div style={{width: "500px"}}>
                    <TaskObjectField
                        modelValue={model.value}
                        onUpdate:modelValue={(val) => model.value = val}
                        schema={{
                            type: "string",
                            title: "Log level",
                            enum: ["DEBUG", "INFO", "WARNING", "ERROR"],
                        }}
                        fieldKey="level"
                        task={{}}
                    />
                </div>
                <pre data-testid="result">{JSON.stringify(model.value)}</pre>
            </div>
        },
    }),
    args: {modelValue: "INFO"},
};

export const RequiredField: Story = {
    render: (args) => ({
        setup() {
            provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
            const model = ref(args.modelValue);
            return () => <div style={{display: "flex", gap: "16px"}}>
                <div style={{width: "500px"}}>
                    <TaskObjectField
                        modelValue={model.value}
                        onUpdate:modelValue={(val) => model.value = val}
                        schema={{type: "string", title: "Task identifier"}}
                        fieldKey="id"
                        task={{}}
                        required={["id"]}
                    />
                </div>
                <pre data-testid="result">{JSON.stringify(model.value)}</pre>
            </div>
        },
    }),
    args: {modelValue: "myTask"},
};

export const DisabledField: Story = {
    render: (args) => ({
        setup() {
            provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
            const model = ref(args.modelValue);
            return () => <div style={{display: "flex", gap: "16px"}}>
                <div style={{width: "500px"}}>
                    <TaskObjectField
                        modelValue={model.value}
                        onUpdate:modelValue={(val) => model.value = val}
                        schema={{type: "string", title: "Locked value"}}
                        fieldKey="locked"
                        task={{}}
                        disabled
                    />
                </div>
                <pre data-testid="result">{JSON.stringify(model.value)}</pre>
            </div>
        },
    }),
    args: {modelValue: "Cannot edit"},
};
