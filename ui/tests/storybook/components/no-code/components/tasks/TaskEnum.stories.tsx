import {computed, provide, ref} from "vue";
import TaskEnum from "../../../../../../src/components/no-code/components/tasks/TaskEnum.vue";
import {Meta, StoryObj} from "@storybook/vue3-vite";
import {expect, fireEvent, waitFor, within} from "storybook/test";
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

export const WithSchemaDefault: Story = {
    render,
    args: {
        modelValue: undefined,
        schema: {
            type: "string",
            enum: ["QUEUE", "CANCEL", "FAIL"],
            default: "QUEUE",
        },
        root: "concurrency.behavior",
    },
    async play({canvasElement}) {
        const canvas = within(canvasElement);
        const result = canvas.getByTestId("result");

        // The schema default must not render as a selection when the value is unset.
        expect(result.textContent).toBe("");

        // Poppers teleport to body and earlier stories leave theirs behind hidden, so
        // count only the open one.
        const openPopper = () =>
            [...canvasElement.ownerDocument.querySelectorAll<HTMLElement>(".kel-select__popper")]
                .find(popper => popper.style.display !== "none");

        // Picking the default value on the first try must write it — before the fix the
        // select pretended QUEUE was selected, so choosing it emitted nothing.
        const combobox = await canvas.findByRole("combobox", {}, {timeout: 15000});
        fireEvent.click(combobox);
        await waitFor(() => expect(openPopper()).toBeDefined());
        const option = await within(openPopper()!).findByText("QUEUE");
        fireEvent.click(option);
        await waitFor(() => expect(result.textContent).toBe("\"QUEUE\""));
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
