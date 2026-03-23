import {ref} from "vue";
import TaskBoolean from "../../../../../../src/components/no-code/components/tasks/TaskBoolean.vue";
import {Meta, StoryObj} from "@storybook/vue3-vite";
import {expect, within, fireEvent} from "storybook/test";

const meta: Meta<typeof TaskBoolean> = {
    title: "Components/NoCode/TaskBoolean",
    component: TaskBoolean,
};

export default meta;

type Story = StoryObj<typeof TaskBoolean>;

const render: Story["render"] = (args) => ({
    setup() {
        const model = ref(args.modelValue ?? false);
        return () => <div style={{display: "flex", gap: "16px"}}>
            <div>
                <TaskBoolean
                    modelValue={model.value}
                    onUpdate:modelValue={(val) => model.value = val}
                />
            </div>
            <pre data-testid="result">{JSON.stringify(model.value)}</pre>
        </div>
    },
});

export const Default: Story = {
    render,
    args: {modelValue: false},
};

export const InitiallyTrue: Story = {
    render,
    args: {modelValue: true},
};

export const Toggle: Story = {
    render,
    args: {modelValue: false},
    async play({canvasElement}) {
        const canvas = within(canvasElement);
        const switchEl = canvasElement.querySelector(".el-switch") as HTMLElement;
        expect(canvas.getByTestId("result").textContent).toBe("false");
        await fireEvent.click(switchEl);
        expect(canvas.getByTestId("result").textContent).toBe("true");
    },
};
