import {computed, provide, ref} from "vue";
import TaskDict from "../../../../../../src/components/no-code/components/tasks/TaskDict.vue";
import Wrapper from "../../../../../../src/components/no-code/components/tasks/Wrapper.vue";
import {userEvent, waitFor, within, expect} from "storybook/internal/test";
import {Meta, StoryObj} from "@storybook/vue3-vite";
import {vueRouter} from "storybook-vue3-router";
import {SCHEMA_DEFINITIONS_INJECTION_KEY} from "../../../../../../src/components/no-code/injectionKeys";

const meta: Meta<typeof TaskDict> = {
    title: "components/nocode/TaskDict",
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
        provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
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

export const ValuesAsObjects: Story = {
    render(args){
        return {
            setup() {
                const model = ref(args.modelValue || {});

                provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
                return () => <div style={{width: "1200px", display: "flex", gap: "20px"}}>
                    <Wrapper>
                        {{
                            tasks: () => <TaskDict modelValue={model.value} schema={{
                                additionalProperties: {
                                    "type": "object",
                                    "properties": {
                                        "binding": {
                                            "type": "string",
                                            "enum": [
                                                "io.kestra.core.tasks.scripts.Bash",
                                                "io.kestra.core.tasks.scripts.Python",
                                                "io.kestra.core.tasks.scripts.JavaScript"
                                            ]
                                        },
                                        "description": {
                                            "type": "string"
                                        },
                                        "script": {
                                            "type": "string"
                                        }
                                    }
                                }
                            }} onUpdate:modelValue={val => model.value = val}/>
                        }}
                    </Wrapper>
                    <pre data-testid="sb-meta-data-result" style={{background: "var(--ks-background-card)", padding: "10px", borderRadius: "4px", width: "100%"}}>
                        {JSON.stringify(model.value, null, 2)}
                    </pre>
                </div>
            }
        }
    },
    args: {
        modelValue: {
            "task1": {
                "binding": "io.kestra.core.tasks.scripts.Bash",
                "description": "A bash task",
                "script": "echo 'Hello World'"
            },
            "task2": {
                "binding": "io.kestra.core.tasks.scripts.Python",
                "description": "A python task",
                "script": "print('Hello World')"
            },
            "task3": {
                "binding": "io.kestra.core.tasks.scripts.JavaScript",
                "description": "A javascript task",
                "script": "console.log('Hello World')"
            }
        }
    }
}

export const ValuesAsTaskLists: Story = {
    render(args){
        return {
            setup() {
                const model = ref(args.modelValue || {});

                provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
                return () => <div style={{width: "1200px", display: "flex", gap: "20px"}}>
                    <Wrapper>
                        {{
                            tasks: () => <TaskDict root="layout" modelValue={model.value} schema={{
                                additionalProperties: {
                                    type: "array",
                                    items: {
                                        anyOf: [   
                                            "Python", 
                                            "Bash", 
                                            "JavaScript", 
                                        ].map(lang => ({
                                            type: "object",
                                            properties: {
                                                id: {type: "string"},
                                                type: {"const": `io.kestra.core.tasks.scripts.${lang}`},
                                            }
                                        })),
                                    }
                                }
                            }} onUpdate:modelValue={val => model.value = val}/>
                        }}
                    </Wrapper>
                    <pre data-testid="sb-meta-data-result" style={{background: "var(--ks-background-card)", padding: "10px", borderRadius: "4px", width: "100%"}}>
                        {JSON.stringify(model.value, null, 2)}
                    </pre>
                </div>
            }
        }
    },
    args: {
        modelValue: {
            "taskList1": [
                {
                    "id": "task1",
                    "type": "io.kestra.core.tasks.scripts.Bash"
                },
                {
                    "id": "task2",
                    "type": "io.kestra.core.tasks.scripts.Python"
                }
            ],
            "taskList2": [
                {
                    "id": "task3",
                    "type": "io.kestra.core.tasks.scripts.JavaScript"
                }
            ]
        }
    }
}