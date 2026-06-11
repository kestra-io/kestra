import LabelInput from "../../../../src/components/labels/LabelInput.vue";
import {ref} from "vue";
import {Meta, StoryFn} from "@storybook/vue3";

export default {
  title: "Components/Labels/LabelInput",
  component: LabelInput,
} as Meta<typeof LabelInput>;

const Template: StoryFn<typeof LabelInput> = (args) => ({
  setup() {
    const model = ref(args.labels);
    return () => <LabelInput {...args} labels={model.value} onUpdate:labels={(labs) => model.value = labs}/>;
  }
});

export const Default = Template.bind({});
Default.args = {
  labels: [],
};

export const WithValue = Template.bind({});
WithValue.args = {
  labels: [{
    key: "example-label",
    value: "example-value",
  }],
};

export const WithExistingLabels = Template.bind({});
WithExistingLabels.args = {
  labels: [{
    key: "existing-label",
    value: "existing-value",
  }],
  existingLabels: [{
    key: "existing-label",
    value: "existing-value",
  }],
};