import {computed, provide, ref} from "vue";
import TaskString from "../../../../../../src/components/no-code/components/tasks/TaskString.vue";
import {Meta, StoryObj} from "@storybook/vue3-vite";
import {expect} from "storybook/test";
import {vueRouter} from "storybook-vue3-router";
import {SCHEMA_DEFINITIONS_INJECTION_KEY} from "../../../../../../src/components/no-code/injectionKeys";

const meta: Meta<typeof TaskString> = {
    title: "Components/NoCode/TaskString",
    component: TaskString,
    decorators: [
        vueRouter([{path: "/", name: "home", component: {template: "<div>home</div>"}}]),
    ],
};

export default meta;

type Story = StoryObj<typeof TaskString>;

const render: Story["render"] = (args) => ({
    setup() {
        provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
        const model = ref(args.modelValue);
        return () => <div style={{display: "flex", gap: "16px"}}>
            <div style={{width: "500px"}}>
                <TaskString
                    modelValue={model.value}
                    onUpdate:modelValue={(val) => model.value = val}
                    schema={args.schema}
                    root={args.root}
                    disabled={args.disabled}
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
        schema: {type: "string"} as any,
        root: "description",
    },
};

export const WithValue: Story = {
    render,
    args: {
        modelValue: "Hello, World!",
        schema: {type: "string"} as any,
        root: "description",
    },
};

export const DateTimePicker: Story = {
    render,
    args: {
        modelValue: undefined,
        schema: {type: "string", format: "date-time"} as any,
        root: "startDate",
    },
};

export const DurationPicker: Story = {
    render,
    args: {
        modelValue: "PT1H30M",
        schema: {type: "string", format: "duration"} as any,
        root: "timeout",
    },
};

export const Disabled: Story = {
    render,
    args: {
        modelValue: "Read-only value",
        schema: {type: "string"} as any,
        root: "locked",
        disabled: true,
    },
    play: async ({canvasElement}) => {
        // A locked field must carry the greyed disabled treatment, not just the lock
        // icon, so it reads the same as the locked namespace select next to it.
        const input = canvasElement.querySelector("input") as HTMLInputElement;
        expect(input.disabled).toBe(true);
        expect(canvasElement.querySelector(".kel-input.is-disabled")).toBeTruthy();
        expect(canvasElement.querySelector(".lock-icon")).toBeTruthy();
    },
};
