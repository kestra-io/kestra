import {ref} from "vue";
import TaskDict from "../../../../../src/components/flows/tasks/TaskDict.vue";
import {userEvent, waitFor, within, expect} from "storybook/internal/test";
import {Meta, StoryObj} from "@storybook/vue3-vite";
import {vueRouter} from "storybook-vue3-router";

const meta: Meta<typeof TaskDict> = {
    title: "components/flows/tasks/TaskDict",
    component: TaskDict,
    decorators: [
            vueRouter([
                {
                    path: "/",
                    name: "home",
                    component: TaskDict
                }
            ])
        ],
}

export default meta;

type Story = StoryObj<typeof TaskDict>;

const render: Story["render"] = (args) => ({
    components: {TaskDict},
    setup() {
        const model = ref(args.modelValue || {});
        return () => <>
            <TaskDict modelValue={model.value} schema={{}} onUpdate:modelValue={val => model.value = val}/>
            <pre data-testid="sb-meta-data-result">
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
        const newLine = within(await canvas.findByTestId("task-dict-item--3"));

        const newKeyField = await newLine.getByPlaceholderText("Key")

        // first test with a duplicated value and make sure there is no error
        await userEvent.type(newKeyField, "key2");

        // find the monaco editor and type in the value
        const monacoEditor = await waitFor(async function monacoInit() {
            const line = await canvas.findByTestId("task-dict-item-key2-3")
            const mon = line?.querySelector(".ks-monaco-editor") as any;
            if (!mon?.__setValueInTests) {
                if(!line)
                    throw new Error("Dict line not found");
                if(!mon)
                    throw new Error("Monaco editor not found");
                throw new Error("Monaco editor not initialized for tests");
            }
            return mon;
        });
        monacoEditor?.__setValueInTests("newValue");

        // if the field disappears because of duplication,
        // this line will error and the test fail
        userEvent.clear(newKeyField);
        userEvent.type(newKeyField, "newKey");

        await waitFor(function valueUpdated() {
            expect(canvas.getByTestId("sb-meta-data-result")?.innerText).toContain("\"newKey\": \"newValue\"");
        });
    }
}