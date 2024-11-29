import InputsForm from "./InputsForm.vue";

const meta = {
  title: "inputs/InputsForm",
  component: InputsForm,
  // This component will have an automatically generated docsPage entry: https://storybook.js.org/docs/writing-docs/autodocs
  tags: ["autodocs"],
}

export default meta;


/**
 *👇 Render functions are a framework specific feature to allow you control on how the component renders.
 * See https://storybook.js.org/docs/api/csf
 * to learn how to use render functions.
 *
 * @type {import("@storybook/vue3").StoryObj}
 */
const Primary = {
  args: {
    initialInputs: [{
        id: "emailInput",
        type: "EMAIL"
    }],
  },
};

export {Primary};
