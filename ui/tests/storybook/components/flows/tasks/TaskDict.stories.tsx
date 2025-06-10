import {ref} from "vue";
import TaskDict from "../../../../../src/components/flows/tasks/TaskDict.vue";
import {userEvent, waitFor, within, expect} from "storybook/internal/test";
import {Meta, StoryObj} from "@storybook/vue3-vite";

const meta: Meta<typeof TaskDict> = {
    title: "components/flows/tasks/TaskDict",
    component: TaskDict,
}

export default meta;

type Story = StoryObj<typeof TaskDict>;

const render: Story["render"] = (args) => ({
    components: {TaskDict},
    setup() {
        const model = ref(args.modelValue || {});
        return () => <>
            <TaskDict modelValue={model.value} schema={{}} onUpdate:modelValue={val => model.value = val}/>
            <pre>
                {JSON.stringify(model.value, null, 2)}
            </pre>
        </>
    }
});

export const Default: Story = {
    render,
    args: {
        modelValue: {}
    }
}


export const TestDoubleKey: Story = {
    render,
    args: {
        modelValue: {
            "key1": "value1",
            "key2": "value2",
            "key3": {
                "subKey1": "subValue1",
                "subKey2": "subValue2"
            }
        }
    },
    play: async ({canvasElement}) => {
        const canvas = within(canvasElement);
        userEvent.click(await canvas.findByText("+ Add a new value"));
        const keys = await waitFor(async () => {
            const keysIn = await canvas.findAllByPlaceholderText("Key")
            expect(keysIn.length).toBe(4);
            return keysIn;
        });
        userEvent.type(keys[keys.length - 1], "key4");
    }
}