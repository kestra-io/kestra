import {defineComponent, ref} from "vue";
import {
    userEvent,
    within,
    expect,
    waitFor
} from "@storybook/test";
import InputsForm from "../../../../src/components/inputs/InputsForm.vue";

const meta = {
    title: "inputs/InputsForm",
    component: InputsForm,
}

export default meta;

const Sut = defineComponent((props) => {
    const values = ref({});
    return () => (<>
        <el-form label-position="top">
            <InputsForm initialInputs={props.inputs} modelValue={values.value} onUpdate:modelValue={(value) => values.value = value}/>
        </el-form>
        <pre data-testid="test-content">{
            JSON.stringify(values.value, null, 2)
        }</pre>
    </>)
}, {
    props: {"inputs": {type:Array, required:true}}
})

/**
 * @type {import('@storybook/vue3').StoryObj<typeof InputsForm>}
 */
export const InputTypes = {
    async play({canvasElement}) {
        const can = within(canvasElement);
        const popups = within(window.document);

        const MonacoEditor = await waitFor(function MonacoEditorReady() {
            const editor = can.getByLabelText("email input").querySelector(".ks-monaco-editor")
            // eslint-disable-next-line @typescript-eslint/no-unused-expressions
            expect(editor).to.exist;
            return editor;
        }, {timeout: 500, interval: 100});
        // wait for the setup to finish
        await waitFor(() => expect(typeof MonacoEditor.__setValueInTests).toBe("function"));
        MonacoEditor.__setValueInTests("foo@example.com")
        await waitFor(function testEmail() {expect(can.getByTestId("test-content").textContent).to.include("foo@example.com")})

        await userEvent.click(can.getByLabelText("Single select input"));
        await userEvent.click(popups.getByText("Second value"));

        await waitFor(function testSelect() {expect(can.getByTestId("test-content").textContent).to.include("Second value")})

        await userEvent.click(can.getByLabelText("Multi select input"));
        await userEvent.click(popups.getByText("Fifth value"));
        await userEvent.click(popups.getByText("Seventh value"));

        await userEvent.keyboard("{esc}");

        await waitFor(function testMultiSelect() {
            expect(can.getByTestId("test-content").textContent)
                .to.include("[\\\"Fifth value\\\",\\\"Seventh value\\\"]")
        })
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
                ],
            }]}/>
    },
}
