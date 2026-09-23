import {computed, provide, ref} from "vue";
import TaskArray from "../../../../../../src/components/no-code/components/tasks/TaskArray.vue";
import Wrapper from "../../../../../../src/components/no-code/components/tasks/Wrapper.vue";
import {Meta, StoryObj} from "@storybook/vue3-vite";
import {expect, within, fireEvent, waitFor} from "storybook/test";
import {vueRouter} from "storybook-vue3-router";
import {SCHEMA_DEFINITIONS_INJECTION_KEY} from "../../../../../../src/components/no-code/injectionKeys";

const meta: Meta<typeof TaskArray> = {
    title: "Components/NoCode/TaskArray",
    component: TaskArray,
    decorators: [
        vueRouter([{path: "/", name: "home", component: {template: "<div>home</div>"}}]),
    ],
};

export default meta;

type Story = StoryObj<typeof TaskArray>;

export const StringArray: Story = {
    render: (args) => ({
        setup() {
            provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
            const model = ref(args.modelValue);
            return () => <div style={{display: "flex", gap: "16px"}}>
                <div style={{width: "500px"}}>
                    <Wrapper>
                        {{
                            tasks: () => <TaskArray
                                modelValue={model.value}
                                onUpdate:modelValue={(val) => model.value = val}
                                schema={args.schema}
                                root="items"
                            />
                        }}
                    </Wrapper>
                </div>
                <pre data-testid="result">{JSON.stringify(model.value, null, 2)}</pre>
            </div>
        },
    }),
    args: {
        modelValue: ["first", "second", "third"],
        schema: {
            type: "array",
            items: {type: "string"},
        },
    },
};

export const EmptyArray: Story = {
    render: (args) => ({
        setup() {
            provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
            const model = ref(args.modelValue);
            return () => <div style={{display: "flex", gap: "16px"}}>
                <div style={{width: "500px"}}>
                    <Wrapper>
                        {{
                            tasks: () => <TaskArray
                                modelValue={model.value}
                                onUpdate:modelValue={(val) => model.value = val}
                                schema={args.schema}
                                root="tags"
                            />
                        }}
                    </Wrapper>
                </div>
                <pre data-testid="result">{JSON.stringify(model.value, null, 2)}</pre>
            </div>
        },
    }),
    args: {
        modelValue: undefined,
        schema: {
            type: "array",
            items: {type: "string"},
        },
    },
    async play({canvasElement}) {
        const canvas = within(canvasElement);
        await fireEvent.click(await canvas.findByText("+ Add to tags", undefined, {timeout: 4000}));
        await waitFor(() => {
            expect(canvas.getByTestId("result").textContent).toContain("[\n  \"\"\n]");
        });
    },
};

export const NullArray: Story = {
    render: (args) => ({
        setup() {
            provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
            const model = ref(args.modelValue);
            return () => <div style={{display: "flex", gap: "16px"}}>
                <div style={{width: "500px"}}>
                    <Wrapper>
                        {{
                            tasks: () => <TaskArray
                                modelValue={model.value}
                                onUpdate:modelValue={(val) => model.value = val}
                                schema={args.schema}
                                root="tags"
                            />
                        }}
                    </Wrapper>
                </div>
                <pre data-testid="result">{JSON.stringify(model.value, null, 2)}</pre>
            </div>
        },
    }),
    args: {
        modelValue: null,
        schema: {
            type: "array",
            items: {type: "string"},
        },
    },
    async play({canvasElement}) {
        const canvas = within(canvasElement);
        // A `null` value (e.g. from a hand-authored `tags:` line) must render exactly like
        // `undefined` — an empty list with just the "add" affordance, not a phantom item.
        await canvas.findByText("+ Add to tags", undefined, {timeout: 4000});
        expect(canvasElement.querySelectorAll(".array-value-col").length).toBe(0);

        await fireEvent.click(canvas.getByText("+ Add to tags"));
        await waitFor(() => {
            expect(canvas.getByTestId("result").textContent).toContain("[\n  \"\"\n]");
        });
    },
};

export const RequiredNullArray: Story = {
    render: (args) => ({
        setup() {
            provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
            const model = ref(args.modelValue);
            return () => <div style={{display: "flex", gap: "16px"}}>
                <div style={{width: "500px"}}>
                    <Wrapper>
                        {{
                            tasks: () => <TaskArray
                                modelValue={model.value}
                                onUpdate:modelValue={(val) => model.value = val}
                                schema={args.schema}
                                required
                                root="commands"
                            />
                        }}
                    </Wrapper>
                </div>
                <pre data-testid="result">{JSON.stringify(model.value, null, 2)}</pre>
            </div>
        },
    }),
    args: {
        modelValue: null,
        schema: {
            type: "array",
            items: {type: "string"},
        },
    },
    async play({canvasElement}) {
        const canvas = within(canvasElement);
        // A required array is still gated by TaskObjectField's own isMissingRequired check
        // (which reads the raw modelValue) — this component itself must not compensate by
        // rendering a phantom `[null]` item just because the field happens to be required.
        await canvas.findByText("+ Add to commands", undefined, {timeout: 4000});
        expect(canvasElement.querySelectorAll(".array-value-col").length).toBe(0);
    },
};

export const ObjectArray: Story = {
    render: (args) => ({
        setup() {
            provide(SCHEMA_DEFINITIONS_INJECTION_KEY, computed(() => ({})));
            const model = ref(args.modelValue);
            return () => <div style={{display: "flex", gap: "16px"}}>
                <div style={{width: "500px"}}>
                    <Wrapper>
                        {{
                            tasks: () => <TaskArray
                                modelValue={model.value}
                                onUpdate:modelValue={(val) => model.value = val}
                                schema={args.schema}
                                root="rows"
                            />
                        }}
                    </Wrapper>
                </div>
                <pre data-testid="result">{JSON.stringify(model.value, null, 2)}</pre>
            </div>
        },
    }),
    args: {
        modelValue: [{key: "value"} as any],
        schema: {
            type: "array",
            items: {type: "object"},
        },
    },
};
