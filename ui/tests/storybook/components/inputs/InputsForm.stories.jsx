import {defineComponent, ref} from "vue";
import {expect, userEvent, waitFor, within} from "storybook/test";
import {vueRouter} from "storybook-vue3-router";
import InputsForm from "../../../../src/components/inputs/InputsForm.vue";
import {useAxios} from "../../../../src/utils/axios.js";

const meta = {
    title: "inputs/InputsForm",
    component: InputsForm,
    decorators: [
                vueRouter([
                    {
                        path: "/",
                        name: "home",
                        component: InputsForm
                    }
                ])
            ],
};

export default meta;

const Sut = defineComponent((props) => {
    const axios = useAxios()

    axios.post = (uri) => {
        if (!uri.endsWith("/validate")) {
            return {data: []}
        }
        return  Promise.resolve({data: {
                "inputs": props.inputs.map(x => ({
                    input: x,
                    enabled: true,
                    isDefault: false,
                    errors: []
                }))
            }
        })}


    const values = ref({});
    return () => (<>
        <el-form label-position="top">
            <InputsForm initialInputs={props.inputs} modelValue={values.value} flow={{namespace: "ns1", id: "flowid1"}}
                        onUpdate:modelValue={(value) => values.value = value}
            />
        </el-form>
        <pre data-testid="test-content">{
            JSON.stringify(values.value, null, 2)
        }</pre>
    </>);
}, {
    props: {"inputs": {type: Array, required: true}}
});

/**
 * @type {import("@storybook/vue3-vite").StoryObj<typeof InputsForm>}
 */
export const InputTypes = {
    async play({canvasElement}) {
        const can = within(canvasElement);
        const popups = within(window.document);

        const MonacoEditor = await waitFor(function MonacoEditorReady() {
            const editor = can.getByTestId("input-form-email").querySelector(".ks-monaco-editor");
            expect(editor).toBeTruthy();
            return editor;
        }, {timeout: 5000, interval: 100});
        // wait for the setup to finish
        await waitFor(() => expect(typeof MonacoEditor.__setValueInTests).toBe("function"));
        MonacoEditor.__setValueInTests("foo@example.com");
        await waitFor(function testEmail() {
            expect(can.getByTestId("test-content").textContent).to.include("foo@example.com");
        });

        const input = await waitFor(() => can.getByLabelText("Single select input"), {timeout: 2000, interval: 500});

        await userEvent.click(input);
        await userEvent.click(popups.getByText("Second value"));

        await waitFor(function testSelect() {
            expect(can.getByTestId("test-content").textContent).to.include("Second value");
        });

        await userEvent.click(can.getByLabelText("Multi select input"));
        await userEvent.click(popups.getByText("Fifth value"));
        await userEvent.click(popups.getByText("Seventh value"));

        await userEvent.keyboard("{esc}");

        await waitFor(function testMultiSelect() {
            expect(can.getByTestId("test-content").textContent)
                .to.include("[\\\"Fifth value\\\",\\\"Seventh value\\\"]");
        });
    },
    render() {
        return <Sut inputs={[
            {
                id: "email",
                type: "EMAIL",
                displayName: "email input"
            },
            {
                id: "resource_type",
                type: "SELECT",
                required: false,
                displayName: "Single select input",
                values: [
                    "First value",
                    "Second value",
                    "Third value",
                    "Fourth value"
                ],
                allowCustomValue: false
            },
            {
                id: "resource_type_multi",
                type: "MULTISELECT",
                displayName: "Multi select input",
                values: [
                    "Fifth value",
                    "Sixth value",
                    "Seventh value",
                    "Eighth value"
                ]
            },
            {
                id: "duration_field",
                type: "DURATION",
                displayName: "Duration select input",
            }]}
        />;
    }
};

/**
 * @type {import("@storybook/vue3-vite").StoryObj<typeof InputsForm>}
 */
export const InputSelect = {
    async play({canvasElement}) {
        const can = within(canvasElement);
        await waitFor(function testDefaultSelectValue() {
           expect(can.getByTestId("test-content")).toHaveTextContent("Second value");
        });
    },
    render() {
        return <Sut inputs={[
            {
                id: "resource_type",
                type: "SELECT",
                required: false,
                defaults: "Second value",
                displayName: "Single select input",
                values: [
                    "First value",
                    "Second value",
                    "Third value",
                    "Fourth value"
                ],
                allowCustomValue: false
            },
           ]}
        />;
    }
};
