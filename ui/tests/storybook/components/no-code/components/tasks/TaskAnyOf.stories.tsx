import {computed, provide, ref} from "vue";
import TaskAnyOf from "../../../../../../src/components/no-code/components/tasks/TaskAnyOf.vue";
import {Meta, StoryObj} from "@storybook/vue3-vite";
import {expect, fireEvent, waitFor, within} from "storybook/test";
import {vueRouter} from "storybook-vue3-router";
import {SCHEMA_DEFINITIONS_INJECTION_KEY} from "../../../../../../src/components/no-code/injectionKeys";

const meta: Meta<typeof TaskAnyOf> = {
    title: "Components/NoCode/TaskAnyOf",
    component: TaskAnyOf,
    decorators: [
        vueRouter([{path: "/", name: "home", component: {template: "<div>home</div>"}}]),
    ],
};

export default meta;

type Story = StoryObj<typeof TaskAnyOf>;

export const SimpleTypes: Story = {
    render: (args) => ({
        setup() {
            provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
            const model = ref(args.modelValue);
            return () => <div style={{display: "flex", gap: "16px"}}>
                <div style={{width: "500px"}}>
                    <TaskAnyOf
                        modelValue={model.value}
                        onUpdate:modelValue={(val) => model.value = val}
                        schema={args.schema}
                    />
                </div>
                <pre data-testid="result">{JSON.stringify(model.value, null, 2)}</pre>
            </div>
        },
    }),
    args: {
        modelValue: undefined,
        schema: {
            anyOf: [
                {type: "string"} as any,
                {type: "number"} as any,
                {type: "boolean"} as any,
            ],
        } as any,
    },
};

export const ArrayVariants: Story = {
    render: (args) => ({
        setup() {
            provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
            const model = ref(args.modelValue);
            return () => <div style={{display: "flex", gap: "16px"}}>
                <div style={{width: "500px"}}>
                    <TaskAnyOf
                        modelValue={model.value}
                        onUpdate:modelValue={(val) => model.value = val}
                        schema={args.schema}
                    />
                </div>
                <pre data-testid="result">{JSON.stringify(model.value, null, 2)}</pre>
            </div>
        },
    }),
    args: {
        modelValue: undefined,
        schema: {
            anyOf: [
                {type: "array", items: {type: "string"}} as any,
                {type: "array", items: {type: "number"}} as any,
            ],
        } as any,
    },
};

const renderWithResult: Story["render"] = (args) => ({
    setup() {
        provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
        const model = ref(args.modelValue);
        return () => <div style={{display: "flex", gap: "16px"}}>
            <div style={{width: "500px"}}>
                <TaskAnyOf
                    modelValue={model.value}
                    onUpdate:modelValue={(val) => model.value = val}
                    schema={args.schema}
                />
            </div>
            <pre data-testid="result">{JSON.stringify(model.value, null, 2)}</pre>
        </div>
    },
});

const openPopper = (canvasElement: HTMLElement) =>
    [...canvasElement.ownerDocument.querySelectorAll<HTMLElement>(".kel-select__popper")]
        .find(popper => popper.style.display !== "none");

// The shape the backend emits for `@TimezoneId` fields (Schedule `timezone`): branches that only constrain the
// string value, so the field is a single select over the enum that still takes an offset matching the pattern.
export const EnumAndPatternBranches: Story = {
    render: renderWithResult,
    args: {
        modelValue: undefined,
        schema: {
            type: "string",
            anyOf: [
                {enum: ["UTC", "Europe/Paris", "Asia/Tokyo"]} as any,
                {pattern: "^(Z|[+-]\\d{2}(:?\\d{2})?)$"} as any,
            ],
        } as any,
    },
    async play({canvasElement}) {
        const canvas = within(canvasElement);
        const result = canvas.getByTestId("result");

        expect(canvas.queryByText("Unknown Schema")).toBeNull();

        const combobox = await canvas.findByRole("combobox", {}, {timeout: 15000});
        fireEvent.click(combobox);
        await waitFor(() => expect(openPopper(canvasElement)).toBeDefined());
        fireEvent.click(await within(openPopper(canvasElement)!).findByText("Europe/Paris"));
        await waitFor(() => expect(result.textContent).toBe("\"Europe/Paris\""));

        fireEvent.click(combobox);
        await waitFor(() => expect(openPopper(canvasElement)).toBeDefined());
        fireEvent.input(combobox, {target: {value: "+02:00"}});
        await within(openPopper(canvasElement)!).findByText("+02:00");
        fireEvent.keyDown(combobox, {key: "Enter", code: "Enter"});
        await waitFor(() => expect(result.textContent).toBe("\"+02:00\""));
    },
};

export const PatternOnlyBranches: Story = {
    render: renderWithResult,
    args: {
        modelValue: undefined,
        schema: {
            type: "string",
            anyOf: [
                {pattern: "^[a-z]+$"} as any,
                {pattern: "^[0-9]+$"} as any,
            ],
        } as any,
    },
    async play({canvasElement}) {
        const canvas = within(canvasElement);

        expect(canvas.queryByText("Unknown Schema")).toBeNull();
        expect(canvas.queryByRole("combobox")).toBeNull();
    },
};
