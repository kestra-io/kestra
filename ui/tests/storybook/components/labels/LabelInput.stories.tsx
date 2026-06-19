import LabelInput from "../../../../src/components/labels/LabelInput.vue";
import {ref} from "vue";
import {Meta, StoryFn} from "@storybook/vue3";
import {within, userEvent, expect, waitFor} from "storybook/test";

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
Default.play = async ({canvasElement}) => {
  const canvas = within(canvasElement);
  await expect(canvas.getByRole("button", {name: /add label/i})).toBeVisible();
  await expect(canvasElement.querySelectorAll(".label-input-row").length).toBe(1);
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
WithExistingLabels.play = async ({canvasElement}) => {
  const rows = canvasElement.querySelectorAll(".label-input-row");
  await expect(rows.length).toBe(1);
  const key = rows[0].querySelector("input") as HTMLInputElement;
  await expect(key.disabled).toBe(true);
  await expect(rows[0].querySelectorAll("button").length).toBe(1);

  await userEvent.click(within(canvasElement).getByRole("button", {name: /add label/i}));
  await waitFor(() => expect(canvasElement.querySelectorAll(".label-input-row").length).toBe(2));
};