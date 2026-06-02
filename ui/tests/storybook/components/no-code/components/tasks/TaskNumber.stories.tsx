import {computed, provide, ref} from "vue";
import TaskNumber from "../../../../../../src/components/no-code/components/tasks/TaskNumber.vue";
import {Meta, StoryObj} from "@storybook/vue3-vite";
import {vueRouter} from "storybook-vue3-router";
import {SCHEMA_DEFINITIONS_INJECTION_KEY} from "../../../../../../src/components/no-code/injectionKeys";

const meta: Meta<typeof TaskNumber> = {
    title: "Components/NoCode/TaskNumber",
    component: TaskNumber,
    decorators: [
        vueRouter([{path: "/", name: "home", component: {template: "<div>home</div>"}}]),
    ],
};

export default meta;

type Story = StoryObj<typeof TaskNumber>;

const render: Story["render"] = (args: any) => ({
    setup() {
        provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
        const model = ref<number | undefined>(args.modelValue);
        return () => <div style={{display: "flex", gap: "16px"}}>
            <div style={{width: "400px"}}>
                <TaskNumber
                    modelValue={model.value}
                    onUpdate:modelValue={(val: any) => model.value = val}
                    schema={args.schema ?? {type: "number"}}
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
        schema: {type: "number"},
    },
};

export const WithValue: Story = {
    render,
    args: {
        modelValue: 42,
        schema: {type: "number"},
    },
};

export const WithMinMax: Story = {
    render,
    args: {
        modelValue: 5,
        schema: {type: "number", minimum: 0, maximum: 100, step: 5},
    },
};
