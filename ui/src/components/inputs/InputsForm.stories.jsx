import InputsForm from "./InputsForm.vue";

const meta = {
  title: "inputs/InputsForm",
  component: InputsForm,
  // This component will have an automatically generated docsPage entry: https://storybook.js.org/docs/writing-docs/autodocs
  tags: ["autodocs"],
}

export default meta;


export const EmailInput = {
  render() {
    return (<el-form label-position="top">
                <InputsForm initialInputs={[{
                    type:"EMAIL",
                    displayName:"email input"
                }]}/>
        </el-form>)
  },
}
