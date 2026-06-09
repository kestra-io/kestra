import {defineComponent, ref} from "vue";
import {expect, userEvent, waitFor, within} from "storybook/test";
import {vueRouter} from "storybook-vue3-router";
import InputsForm from "../../../../src/components/inputs/InputsForm.vue";
import {flattenInputs} from "../../../../src/utils/inputs";
import {setMockClient} from "@kestra-io/kestra-sdk"

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
    const axios = {}

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

    setMockClient(axios);

    const values = ref({});
    return () => (<>
        <ks-form label-position="top">
            <InputsForm initialInputs={props.inputs} modelValue={values.value} flow={{namespace: "ns1", id: "flowid1"}}
                        onUpdate:modelValue={(value) => values.value = value}
            />
        </ks-form>
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
    const axios = {}
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
    setMockClient(axios)

    const onRecap = ref(false)
    const values = ref({})
    return () => (<>
        <ks-form label-position="top">
            <InputsForm initialInputs={props.inputs} modelValue={values.value} mode="wizard"
                        flow={{namespace: "ns1", id: "flowid1"}}
                        onUpdate:modelValue={(value) => values.value = value}
                        onUpdate:onRecap={(value) => onRecap.value = value}
            />
        </ks-form>
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

        // Next -> step 2 (the FORM "Environment", showing its dotted child region).
        await userEvent.click(can.getByTestId("wizard-next"));
        await waitFor(() => expect(can.getByTestId("input-form-environment.region")).toBeTruthy());
        expect(can.getByTestId("wizard-back")).toBeTruthy();

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

// Grouped (EE Apps) harness: inputs arrive flat-by-dotted-id (already FORM-expanded by the backend),
// and formGroups carries each FORM's displayName/description keyed by the form id.
const GroupedSut = defineComponent((props) => {
    const axios = {}
    axios.post = (uri) => {
        if (!uri.endsWith("/validate")) {
            return {data: []}
        }
        return Promise.resolve({data: {
            inputs: props.inputs.map(x => ({
                input: x,
                enabled: true,
                isDefault: false,
                errors: [],
            })),
        }})
    }
    setMockClient(axios)

    const values = ref({})
    return () => (
        <ks-form label-position="top">
            <InputsForm initialInputs={props.inputs} modelValue={values.value} mode="grouped"
                        formGroups={props.formGroups}
                        flow={{namespace: "ns1", id: "flowid1"}}
                        onUpdate:modelValue={(value) => values.value = value}
            />
        </ks-form>
    );
}, {
    props: {inputs: {type: Array, required: true}, formGroups: {type: Object, required: true}},
});

/**
 * @type {import("@storybook/vue3-vite").StoryObj<typeof InputsForm>}
 */
export const Grouped = {
    async play({canvasElement}) {
        const can = within(canvasElement);

        // Both flat-dotted FORM children render under a single section header (the header emits once per group,
        // not once per child) carrying the FORM's displayName + description; the ungrouped input gets no header.
        await waitFor(() => expect(can.getByTestId("input-form-environment.region")).toBeTruthy());
        expect(can.getByTestId("input-form-environment.zone")).toBeTruthy();
        expect(can.getByTestId("input-form-api_key")).toBeTruthy();

        const headers = canvasElement.querySelectorAll(".grouped-section-header");
        expect(headers.length).toBe(1);
        expect(headers[0].textContent).toContain("Environment");
        expect(headers[0].textContent).toContain("Pick env");
    },
    render() {
        return <GroupedSut
            inputs={[
                {id: "environment.region", type: "STRING", displayName: "Region"},
                {id: "environment.zone", type: "STRING", displayName: "Zone"},
                {id: "api_key", type: "SECRET", displayName: "API Key"},
            ]}
            formGroups={{environment: {displayName: "Environment", description: "Pick env"}}}
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
