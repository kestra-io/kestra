import {defineComponent, ref} from "vue";
import {expect, userEvent, waitFor, within} from "storybook/test";
import {vueRouter} from "storybook-vue3-router";
import {KsForm} from "@kestra-io/design-system";
import InputsForm from "../../../../src/components/inputs/InputsForm.vue";
import {flattenInputs, unflattenToForms} from "../../../../src/utils/inputs";
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
        <KsForm label-position="top" model={values.value}>
            <InputsForm initialInputs={props.inputs} modelValue={values.value} flow={{namespace: "ns1", id: "flowid1"}}
                        onUpdate:modelValue={(value) => values.value = value}
            />
        </KsForm>
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

        // KsEditor is an async component: the form paints once Monaco's chunk has loaded.
        const MonacoEditor = await waitFor(function MonacoEditorReady() {
            const editor = can.getByTestId("input-form-email").querySelector(".ks-monaco-editor");
            expect(editor).toBeTruthy();
            return editor;
        }, {timeout: 15000, interval: 100});
        // wait for the setup to finish
        await waitFor(() => expect(typeof MonacoEditor.__setValueInTests).toBe("function"));
        MonacoEditor.__setValueInTests("foo@example.com");
        await waitFor(function testEmail() {
            expect(can.getByTestId("test-content").textContent).to.include("foo@example.com");
        });

        const input = await waitFor(() => within(can.getByTestId("input-form-resource_type")).getByRole("combobox"), {timeout: 4000, interval: 500});

        await userEvent.click(input);
        await userEvent.click(popups.getByText("Second value"));

        await waitFor(function testSelect() {
            expect(can.getByTestId("test-content").textContent).to.include("Second value");
        });

        await userEvent.click(within(can.getByTestId("input-form-resource_type_multi")).getByRole("combobox"));
        await userEvent.click(popups.getByText("Fifth value"));
        await userEvent.click(popups.getByText("Seventh value"));

        await userEvent.keyboard("{esc}");

        await waitFor(function testMultiSelect() {
            expect(can.getByTestId("test-content").textContent)
                .to.include("[\\\"Fifth value\\\",\\\"Seventh value\\\"]");
        });

        await waitFor(function testBooleanField() {
            // check that we do not have validation error for boolean field
            // since it has a default value
            expect(can.queryByText("Boolean field for required is required")).toBeNull();
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
                displayName: "Duration field",
            },
            {
                id: "boolean_field",
                type: "BOOL",
                displayName: "Boolean field for required",
                defaults: true  
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
        // The form waits on the async KsEditor, so give the first paint room.
        await waitFor(() => expect(can.getByTestId("input-form-name")).toBeTruthy(), {timeout: 15000});
        expect(can.queryByTestId("wizard-back")).toBeNull();
        expect(can.queryByTestId("inputs-wizard-recap")).toBeNull();
        expect(can.getByTestId("on-recap")).toHaveTextContent("false");

        // Progress bar (KsSteps variant="bar"): one label-less segment per fillable step
        // (name, Environment, team); the current one is "active", the rest "upcoming".
        const bar = can.getByTestId("wizard-progress");
        expect(bar.querySelectorAll(".ks-stepbar__seg")).toHaveLength(3);
        expect(bar.querySelectorAll(".ks-stepbar__seg")[0].className).toContain("is-active");

        // Next -> step 2 (the FORM "Environment", showing its dotted child region).
        await userEvent.click(can.getByTestId("wizard-next"));
        await waitFor(() => expect(can.getByTestId("input-form-environment.region")).toBeTruthy());
        expect(can.getByTestId("wizard-back")).toBeTruthy();
        // The active FORM's displayName shows as a header above its fields.
        expect(canvasElement.querySelector(".wizard-step-title")).toHaveTextContent("Environment");

        // Bar tracks progress: step 0 passed (filled), step 1 ("Environment") now active.
        const segs = can.getByTestId("wizard-progress").querySelectorAll(".ks-stepbar__seg");
        expect(segs[0].className).toContain("is-filled");
        expect(segs[1].className).toContain("is-active");

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

// Apps-shaped wizard harness: Apps has no `flow` prop, so InputsForm takes the emit("validation")
// branch. The parent (BlockForm in EE) owns the validate round-trip and reconstructs the FORM tree
// from flat dotted leaves + formGroups before handing it to InputsForm — we mirror both here. The
// validate callback is DEFERRED into a queue the play function releases manually, so we can observe
// the Next button reading "Loading…" mid-round-trip and prove goNext awaits it.
const AppsWizardSut = defineComponent((props) => {
    const initial = unflattenToForms(props.inputs, props.formGroups)

    const queue = []
    function onValidation(event) {
        // hold the callback; the play function releases it via window.__appsWizardFlush()
        queue.push(() => event.callback({
            inputs: props.inputs.map(x => ({input: x, enabled: true, isDefault: false, errors: []})),
        }))
    }
    window.__appsWizardPending = () => queue.length
    window.__appsWizardFlush = () => queue.splice(0).forEach(fn => fn())

    const onRecap = ref(false)
    const values = ref({})
    return () => (<>
        <ks-form label-position="top">
            <InputsForm initialInputs={initial} modelValue={values.value} mode="wizard"
                        formGroups={props.formGroups}
                        onValidation={onValidation}
                        onUpdate:modelValue={(value) => values.value = value}
                        onUpdate:onRecap={(value) => onRecap.value = value}
            />
        </ks-form>
        <pre data-testid="on-recap">{String(onRecap.value)}</pre>
        <pre data-testid="apps-values">{JSON.stringify(values.value)}</pre>
    </>);
}, {
    props: {inputs: {type: Array, required: true}, formGroups: {type: Object, required: true}},
});

/**
 * @type {import("@storybook/vue3-vite").StoryObj<typeof InputsForm>}
 */
export const AppsWizard = {
    async play({canvasElement}) {
        const can = within(canvasElement);

        // Step 1 (plain "name") renders from the seeded skeleton before any validate resolves,
        // but still behind the async KsEditor's first paint.
        await waitFor(() => expect(can.getByTestId("input-form-name")).toBeTruthy(), {timeout: 15000});
        // Drain the initial mount validate so metadata is populated and its signature recorded.
        await waitFor(() => expect(window.__appsWizardPending()).toBeGreaterThan(0));
        window.__appsWizardFlush();

        // DEDUP: clicking Next without editing anything must NOT re-validate — the current metadata
        // already covers this exact payload. It advances instantly, with no round-trip queued and no
        // loading label (this is the timing-free proof of the skip).
        await userEvent.click(can.getByTestId("wizard-next"));
        await waitFor(() => expect(can.getByTestId("input-form-environment.region")).toBeTruthy());
        expect(window.__appsWizardPending()).toBe(0);
        expect(can.getByTestId("wizard-next")).not.toHaveTextContent("Loading");

        // CHANGE: editing an input makes a validate necessary. goNext fires it and AWAITS the
        // promisified emit; held open past the 500ms threshold, the Next button shows the loading
        // label and the step does NOT advance until the round-trip is released.
        const editor = await waitFor(() => {
            const e = can.getByTestId("input-form-environment.region").querySelector(".ks-monaco-editor");
            expect(e).toBeTruthy();
            return e;
        }, {timeout: 5000, interval: 100});
        await waitFor(() => expect(typeof editor.__setValueInTests).toBe("function"));
        editor.__setValueInTests("eu-west");
        await waitFor(() => expect(can.getByTestId("apps-values")).toHaveTextContent("eu-west"));

        await userEvent.click(can.getByTestId("wizard-next"));
        await waitFor(() => expect(window.__appsWizardPending()).toBeGreaterThan(0)); // validate fired
        await waitFor(() => expect(can.getByTestId("wizard-next")).toHaveTextContent("Loading"), {timeout: 3000});
        expect(can.queryByTestId("inputs-wizard-recap")).toBeNull(); // parked on the await

        // Release the held round-trip -> goNext resumes, clears the loading label, advances to recap.
        window.__appsWizardFlush();
        await waitFor(() => expect(can.getByTestId("inputs-wizard-recap")).toBeTruthy());
        expect(can.getByTestId("on-recap")).toHaveTextContent("true");
    },
    render() {
        return <AppsWizardSut
            inputs={[
                {id: "name", type: "STRING", required: false, displayName: "Name"},
                {id: "environment.region", type: "STRING", required: false, displayName: "Region"},
            ]}
            formGroups={{environment: {displayName: "Environment"}}}
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

// Replay harness: mirrors FlowRun.fillInputsFromExecution — once the form signals ready, every leaf
// is prefilled from a previous execution's `inputs` through the component's prefillInputValue.
const PrefillSut = defineComponent((props) => {
    const axios = {}
    axios.post = (uri) => {
        if (!uri.endsWith("/validate")) {
            return {data: []}
        }
        return Promise.resolve({data: {
            inputs: props.inputs.map(x => ({input: x, enabled: true, isDefault: false, errors: []})),
        }})
    }
    setMockClient(axios)

    const form = ref(null)
    const values = ref({})
    const onReady = () => {
        for (const input of props.inputs) {
            const value = props.executionInputs[input.id]
            if (value !== undefined) {
                form.value?.prefillInputValue(input, value)
            }
        }
    }
    return () => (<>
        <KsForm label-position="top" model={values.value}>
            <InputsForm ref={form} initialInputs={props.inputs} modelValue={values.value}
                        flow={{namespace: "ns1", id: "flowid1"}}
                        onReady={onReady}
                        onUpdate:modelValue={(value) => values.value = value}
            />
        </KsForm>
        <pre data-testid="test-content">{JSON.stringify(values.value, null, 2)}</pre>
    </>);
}, {
    props: {
        "inputs": {type: Array, required: true},
        "executionInputs": {type: Object, required: true}
    }
});

/**
 * @type {import("@storybook/vue3-vite").StoryObj<typeof InputsForm>}
 */
export const PrefillFromExecution = {
    async play({canvasElement}) {
        const can = within(canvasElement);

        // The control itself shows the replayed selection. MULTISELECT binds a different model from
        // the one the submission reads, so it used to render empty while the value was submitted.
        await waitFor(function testMultiSelectShowsSelection() {
            const select = can.getByTestId("input-form-shards");
            expect(select).toHaveTextContent("Fifth value");
            expect(select).toHaveTextContent("Seventh value");
        }, {timeout: 5000, interval: 100});

        // ...and the submission payload carries the same selection, JSON-encoded.
        await waitFor(function testMultiSelectSubmitted() {
            expect(can.getByTestId("test-content").textContent)
                .to.include("[\\\"Fifth value\\\",\\\"Seventh value\\\"]");
        });

        // A SECRET is never prefilled: execution.inputs holds the serialised EncryptedString, so
        // replaying it would submit "[object Object]" to be re-encrypted as the new secret value.
        expect(can.getByTestId("input-form-api_key")).toHaveValue("");
        expect(can.getByTestId("test-content").textContent).not.to.include("aes_encrypted");
        expect(can.getByTestId("test-content").textContent).not.to.include("[object Object]");
    },
    render() {
        return <PrefillSut
            inputs={[
                {
                    id: "shards",
                    type: "MULTISELECT",
                    required: false,
                    displayName: "Shards to process",
                    values: ["Fifth value", "Sixth value", "Seventh value"]
                },
                {id: "api_key", type: "SECRET", required: false, displayName: "Api key"}
            ]}
            executionInputs={{
                shards: ["Fifth value", "Seventh value"],
                api_key: {value: "abc123cipher", type: "io.kestra.datatype:aes_encrypted"}
            }}
        />;
    }
};

// The other two paths that seed MULTISELECT state: a trigger's stored inputs (a real array), and a
// `defaults` value, which crosses the wire JSON-encoded as a string because Property serialises so.
const StatePathSut = defineComponent((props) => {
    const axios = {}
    axios.post = (uri) => {
        if (!uri.endsWith("/validate")) {
            return {data: []}
        }
        return Promise.resolve({data: {
            inputs: props.inputs.map(x => ({
                input: x,
                enabled: true,
                isDefault: x.defaults !== undefined,
                errors: [],
            })),
        }})
    }
    setMockClient(axios)

    const values = ref({})
    return () => (<>
        <KsForm label-position="top" model={values.value}>
            <InputsForm initialInputs={props.inputs} modelValue={values.value}
                        selectedTrigger={props.selectedTrigger}
                        flow={{namespace: "ns1", id: "flowid1"}}
                        onUpdate:modelValue={(value) => values.value = value}
            />
        </KsForm>
        <pre data-testid="test-content">{JSON.stringify(values.value, null, 2)}</pre>
    </>);
}, {
    props: {
        "inputs": {type: Array, required: true},
        "selectedTrigger": {type: Object, required: false, default: undefined}
    }
});

/**
 * @type {import("@storybook/vue3-vite").StoryObj<typeof InputsForm>}
 */
export const MultiSelectStateSources = {
    async play({canvasElement}) {
        const can = within(canvasElement);

        // A trigger's inputs reach both the control and the payload, and the payload gets the
        // JSON-encoded form — assigned raw, FormData would flatten the array to "Fifth value,...".
        await waitFor(function testTriggerPrefill() {
            const select = can.getByTestId("input-form-shards");
            expect(select).toHaveTextContent("Fifth value");
            expect(select).toHaveTextContent("Seventh value");
        }, {timeout: 5000, interval: 100});
        await waitFor(function testTriggerSubmitted() {
            expect(can.getByTestId("test-content").textContent)
                .to.include("[\\\"Fifth value\\\",\\\"Seventh value\\\"]");
        });

        // A string `defaults` is parsed before it reaches the control, so it renders as one option
        // rather than as a single tag reading the raw JSON text.
        const regions = can.getByTestId("input-form-regions");
        expect(regions).toHaveTextContent("eu");
        expect(regions.textContent).not.to.include("[\"eu\"]");
    },
    render() {
        return <StatePathSut
            inputs={[
                {
                    id: "shards",
                    type: "MULTISELECT",
                    required: false,
                    displayName: "Shards to process",
                    values: ["Fifth value", "Sixth value", "Seventh value"]
                },
                {
                    id: "regions",
                    type: "MULTISELECT",
                    required: false,
                    displayName: "Regions",
                    defaults: "[\"eu\"]",
                    values: ["eu", "us", "apac"]
                }
            ]}
            selectedTrigger={{inputs: {shards: ["Fifth value", "Seventh value"]}}}
        />;
    }
};
