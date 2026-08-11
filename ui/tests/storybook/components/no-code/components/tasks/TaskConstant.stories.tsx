import {ref} from "vue";
import TaskConstant from "../../../../../../src/components/no-code/components/tasks/TaskConstant.vue";
import {Meta, StoryObj} from "@storybook/vue3-vite";
import {expect, within} from "storybook/test";

const meta: Meta<typeof TaskConstant> = {
    title: "Components/NoCode/TaskConstant",
    component: TaskConstant,
};

export default meta;

type Story = StoryObj<typeof TaskConstant>;

const render: Story["render"] = (args) => ({
    setup() {
        const model = ref(args.modelValue ?? "");
        return () => <div style={{display: "flex", gap: "16px"}}>
            <div style={{width: "400px"}}>
                <TaskConstant
                    modelValue={model.value}
                    onUpdate:modelValue={(val) => model.value = val}
                    schema={args.schema}
                />
            </div>
            <pre data-testid="result">{JSON.stringify(model.value)}</pre>
        </div>
    },
});

export const Default: Story = {
    render,
    args: {
        modelValue: "",
        schema: {const: "io.kestra.plugin.core.log.Log"},
    },
    async play({canvasElement}) {
        const canvas = within(canvasElement);
        expect(canvas.getByTestId("result").textContent).toBe(
            JSON.stringify("io.kestra.plugin.core.log.Log"),
        );
    },
};

export const WithObjectConst: Story = {
    render,
    args: {
        modelValue: "",
        schema: {const: "FIXED_VALUE"},
    },
};
