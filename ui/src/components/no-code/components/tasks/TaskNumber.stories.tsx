import {computed, provide, ref} from "vue";
import TaskNumber from "./TaskNumber.vue";
import {Meta, StoryObj} from "@storybook/vue3-vite";
import {expect, userEvent, waitFor} from "storybook/test";
import {vueRouter} from "storybook-vue3-router";
import {SCHEMA_DEFINITIONS_INJECTION_KEY} from "../../injectionKeys";

const meta: Meta<typeof TaskNumber> = {
    title: "Components/NoCode/TaskNumber",
    component: TaskNumber,
    decorators: [
        vueRouter([{path: "/", name: "home", component: {template: "<div>home</div>"}}]),
    ],
};

export default meta;

type Story = StoryObj<typeof TaskNumber>;

const render: Story["render"] = (args) => ({
    setup() {
        provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
        const model = ref(args.modelValue);
        return () => <div style={{display: "flex", gap: "16px"}}>
            <div style={{width: "400px"}}>
                <TaskNumber
                    modelValue={model.value}
                    onUpdate:modelValue={(val) => model.value = val}
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

const shown = (canvasElement: HTMLElement) => (canvasElement.querySelector("input") as HTMLInputElement).value;

export const Decimal: Story = {
    render,
    args: {
        modelValue: 2.5,
        schema: {type: "number"},
    },
    play: async ({canvasElement}) => {
        expect(shown(canvasElement)).toBe("2.5");
    },
};

export const Zero: Story = {
    render,
    args: {
        modelValue: 0,
        schema: {type: "number"},
    },
    play: async ({canvasElement}) => {
        expect(shown(canvasElement)).toBe("0");
    },
};

export const NumericString: Story = {
    render,
    args: {
        modelValue: "4.5",
        schema: {type: "number"},
    },
    play: async ({canvasElement}) => {
        expect(shown(canvasElement)).toBe("4.5");
    },
};

export const Expression: Story = {
    render,
    args: {
        modelValue: "{{ inputs.ratio }}",
        schema: {type: "number"},
    },
    play: async ({canvasElement}) => {
        // A Pebble expression is not a number, so the box stays empty instead of showing NaN.
        expect(shown(canvasElement)).toBe("");
    },
};

export const SchemaDefault: Story = {
    render,
    args: {
        modelValue: undefined,
        schema: {type: "number", default: 1.5},
    },
    play: async ({canvasElement}) => {
        expect(shown(canvasElement)).toBe("1.5");
    },
};

export const TypeDecimal: Story = {
    render,
    args: {
        modelValue: undefined,
        schema: {type: "number"},
    },
    play: async ({canvasElement}) => {
        const input = canvasElement.querySelector("input") as HTMLInputElement;
        await userEvent.type(input, "2.5");
        await userEvent.tab();
        await waitFor(() => expect(canvasElement.querySelector("[data-testid='result']")?.textContent).toBe("2.5"));
        expect(shown(canvasElement)).toBe("2.5");
    },
};
