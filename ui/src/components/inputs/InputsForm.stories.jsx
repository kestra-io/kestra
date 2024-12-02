import InputsForm from "./InputsForm.vue";
import {defineComponent, ref} from "vue";

const meta = {
    title: "inputs/InputsForm",
    component: InputsForm,
    // This component will have an automatically generated docsPage entry: https://storybook.js.org/docs/writing-docs/autodocs
    tags: ["autodocs"],
}

export default meta;

const Sut = defineComponent((props) => {
    const values = ref({});
    return () => (<>
        <el-form label-position="top" modelValue={values.value}>
            <InputsForm initialInputs={props.inputs} modelValue={values.value} onUpdate:modelValue={(value) => values.value = value}/>
        </el-form>
        <pre>{
            JSON.stringify(values.value, null, 2)
        }</pre>
    </>)
}, {
    props: {"inputs": {type:Array, required:true}}
})


export const InputTypes = {
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
