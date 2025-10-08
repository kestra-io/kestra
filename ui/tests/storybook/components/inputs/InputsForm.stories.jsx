/* eslint-disable @typescript-eslint/no-unused-expressions */
import {defineComponent, ref} from "vue";
import {expect, userEvent, waitFor, within} from "storybook/test";
import {vueRouter} from "storybook-vue3-router";
import InputsForm from "../../../../src/components/inputs/InputsForm.vue";

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
    const values = ref({});
    return () => (<>
        <el-form label-position="top">
            <InputsForm initialInputs={props.inputs} modelValue={values.value}
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
            // eslint-disable-next-line @typescript-eslint/no-unused-expressions
            expect(editor).to.exist;
            return editor;
        }, {timeout: 2000, interval: 100});
        // wait for the setup to finish
        await waitFor(() => expect(typeof MonacoEditor.__setValueInTests).toBe("function"));
        MonacoEditor.__setValueInTests("foo@example.com");
        await waitFor(function testEmail() {
            expect(can.getByTestId("test-content").textContent).to.include("foo@example.com");
        });

        const singleSelect = await waitFor(() => can.getByTestId("input-form-resource_type"));
        
        await userEvent.click(singleSelect);
        
        await waitFor(() => {
            const dropdown = document.querySelector(".el-select-dropdown");
            expect(dropdown).to.exist;
            expect(dropdown).to.be.visible;
        }, {timeout: 2000});
        
        const secondOption = await waitFor(() => {
            const options = Array.from(document.querySelectorAll(".el-select-dropdown__item"));
            const option = options.find(el => el.textContent.includes("Second value"));
            expect(option).to.exist;
            return option;
        }, {timeout: 2000});
        
        await userEvent.click(secondOption);

        await waitFor(function testSelect() {
            expect(can.getByTestId("test-content").textContent).to.include("Second value");
        });

        await userEvent.click(can.getByTestId("input-form-resource_type_multi"));
        
        await waitFor(() => {
            const dropdown = document.querySelector(".el-select-dropdown");
            expect(dropdown).to.exist;
            expect(dropdown).to.be.visible;
        }, {timeout: 2000});
        
        const fifthOption = await waitFor(() => {
            const options = Array.from(document.querySelectorAll(".el-select-dropdown__item"));
            const option = options.find(el => el.textContent.includes("Fifth value"));
            expect(option).to.exist;
            return option;
        }, {timeout: 2000});
        
        await userEvent.click(fifthOption);
        
        const seventhOption = await waitFor(() => {
            const options = Array.from(document.querySelectorAll(".el-select-dropdown__item"));
            const option = options.find(el => el.textContent.includes("Seventh value"));
            expect(option).to.exist;
            return option;
        }, {timeout: 2000});
        
        await userEvent.click(seventhOption);

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
