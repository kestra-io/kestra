import {defineComponent, ref} from "vue";
import {
    userEvent,
    within,
    expect,
} from "@storybook/test";
import InputsForm from "./InputsForm.vue";

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
        await userEvent.type(can.getByLabelText("email input"), "foo@example.com");
        await expect(can.getByTestId("test-content").textContent).to.include("foo@example.com")
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
                displayName: "Resource Type",
                values: [
                    "Access permissions",
                    "SaaS applications",
                    "Development tool",
                    "Cloud VM"
                ],
                allowCustomValue: false
            },
            {
                id: "resource_type_multi",
                type: "MULTISELECT",
                displayName: "Multi select",
                values: [
                    "Access permissions",
                    "SaaS applications",
                    "Development tool",
                    "Cloud VM"
                ],
            }]}/>
    },
}
