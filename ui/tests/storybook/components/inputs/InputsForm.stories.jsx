import {defineComponent, ref} from "vue";
import {expect, userEvent, waitFor, within} from "storybook/test";
import {vueRouter} from "storybook-vue3-router";
import InputsForm from "../../../../src/components/inputs/InputsForm.vue";
import {useAxios} from "../../../../src/utils/axios.js";
import {flattenInputs} from "../../../../src/utils/inputs";

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

        const input = await waitFor(() => can.getByLabelText("Single select input"), {timeout: 4000, interval: 500});

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

// Wizard harness: the validate mock expands FORM groups to dotted leaves, exactly like the
// backend, so InputsForm receives the same flat-by-dotted-id metadata it does in production.
const WizardSut = defineComponent((props) => {
    const axios = useAxios()
    axios.post = (uri) => {
        if (!uri.endsWith("/validate")) {
            return {data: []}
        }
        return Promise.resolve({data: {
            inputs: flattenInputs(props.inputs).map(x => ({
                input: x,
                enabled: true,
                isDefault: false,
                errors: [],
            })),
        }})
    }

    const onRecap = ref(false)
    const values = ref({})
    return () => (<>
        <el-form label-position="top">
            <InputsForm initialInputs={props.inputs} modelValue={values.value} mode="wizard"
                        flow={{namespace: "ns1", id: "flowid1"}}
                        onUpdate:modelValue={(value) => values.value = value}
                        onUpdate:onRecap={(value) => onRecap.value = value}
            />
        </el-form>
        <pre data-testid="on-recap">{String(onRecap.value)}</pre>
    </>);
}, {
    props: {"inputs": {type: Array, required: true}}
});

/**
 * @type {import("@storybook/vue3-vite").StoryObj<typeof InputsForm>}
 */
export const Wizard = {
    async play({canvasElement}) {
        const can = within(canvasElement);

        // Step 1 (plain "name"): Next visible, Back hidden, not on recap yet.
        await waitFor(() => expect(can.getByTestId("input-form-name")).toBeTruthy());
        expect(can.queryByTestId("wizard-back")).toBeNull();
        expect(can.queryByTestId("inputs-wizard-recap")).toBeNull();
        expect(can.getByTestId("on-recap")).toHaveTextContent("false");

        // Progress stepper (el-steps): one step per fillable step ("Inputs", "Environment", "Inputs"),
        // the current one in the "process" state.
        const stepper = can.getByTestId("wizard-steps");
        expect(stepper.querySelectorAll(".el-step")).toHaveLength(3);
        expect(stepper).toHaveTextContent("Environment");
        expect(stepper.querySelectorAll(".el-step__head")[0].className).toContain("is-process");

        // Next -> step 2 (the FORM "Environment", showing its dotted child region).
        await userEvent.click(can.getByTestId("wizard-next"));
        await waitFor(() => expect(can.getByTestId("input-form-environment.region")).toBeTruthy());
        expect(can.getByTestId("wizard-back")).toBeTruthy();

        // Stepper tracks progress: step 0 completed (success), step 1 ("Environment") now in process.
        expect(stepper.querySelectorAll(".el-step__head")[0].className).toContain("is-success");
        expect(stepper.querySelectorAll(".el-step__head")[1].className).toContain("is-process");

        // Next -> step 3 (plain "team").
        await userEvent.click(can.getByTestId("wizard-next"));
        await waitFor(() => expect(can.getByTestId("input-form-team")).toBeTruthy());

        // Next -> recap: every section listed, Execute lives in the footer so onRecap flips true.
        await userEvent.click(can.getByTestId("wizard-next"));
        await waitFor(() => expect(can.getByTestId("inputs-wizard-recap")).toBeTruthy());
        expect(can.getByTestId("on-recap")).toHaveTextContent("true");
        expect(can.queryByTestId("wizard-next")).toBeNull(); // no Next on recap

        // Edit the FORM section -> jump back to step 2, primary button now reads "Done".
        await userEvent.click(can.getByTestId("recap-edit-1"));
        await waitFor(() => expect(can.getByTestId("input-form-environment.region")).toBeTruthy());
        expect(can.getByTestId("wizard-next")).toHaveTextContent("Done");

        // Done returns straight to the recap (not the next sequential step).
        await userEvent.click(can.getByTestId("wizard-next"));
        await waitFor(() => expect(can.getByTestId("inputs-wizard-recap")).toBeTruthy());
    },
    render() {
        return <WizardSut inputs={[
            {id: "name", type: "STRING", required: false, displayName: "Name"},
            {
                id: "environment",
                type: "FORM",
                displayName: "Environment",
                inputs: [{id: "region", type: "STRING", required: false, displayName: "Region"}],
            },
            {id: "team", type: "STRING", required: false, displayName: "Team"},
        ]}
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
