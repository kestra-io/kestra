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
  const addButton = canvas.getByRole("button", {name: /add label/i});
  await expect(addButton).toBeVisible();
  await expect(addButton.closest(".label-input-header")).not.toBeNull();
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

const HeaderSlotsTemplate: StoryFn<typeof LabelInput> = (args) => ({
  setup() {
    const model = ref(args.labels);
    return () => (
      <LabelInput {...args} labels={model.value} onUpdate:labels={(labs) => model.value = labs}>
        {{
          header: () => <span style="font-weight: 600">Set labels</span>,
          "header-end": () => <button aria-label="Close">✕</button>,
        }}
      </LabelInput>
    );
  }
});

export const WithHeaderSlots = HeaderSlotsTemplate.bind({});
WithHeaderSlots.args = {
  labels: [{
    key: "example-label",
    value: "example-value",
  }],
};
WithHeaderSlots.play = async ({canvasElement}) => {
  const canvas = within(canvasElement);
  const addButton = canvas.getByRole("button", {name: /add label/i});
  const closeButton = canvas.getByRole("button", {name: /close/i});
  await expect(addButton).toBeVisible();
  await expect(closeButton).toBeVisible();
  const header = canvasElement.querySelector(".label-input-header") as HTMLElement;
  await expect(header.contains(canvas.getByText("Set labels"))).toBe(true);
  await expect(header.contains(addButton)).toBe(true);
  await expect(header.contains(closeButton)).toBe(true);
  // the Add button comes before host extras inside the actions cluster
  await expect(addButton.compareDocumentPosition(closeButton) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
};

export const WithHeaderSlotsDark = HeaderSlotsTemplate.bind({});
WithHeaderSlotsDark.args = WithHeaderSlots.args;
WithHeaderSlotsDark.parameters = {
  themes: {themeOverride: "dark"},
};

const NarrowLongTitleTemplate: StoryFn<typeof LabelInput> = (args) => ({
  setup() {
    const model = ref(args.labels);
    return () => (
      <div style="width: 400px">
        <LabelInput {...args} labels={model.value} onUpdate:labels={(labs) => model.value = labs}>
          {{
            header: () => <span style="white-space: nowrap; overflow: hidden; text-overflow: ellipsis; display: block">A very long title that should truncate instead of pushing the actions out of the popover</span>,
          }}
        </LabelInput>
      </div>
    );
  }
});

export const NarrowLongTitle = NarrowLongTitleTemplate.bind({});
NarrowLongTitle.args = {
  labels: [],
};
NarrowLongTitle.play = async ({canvasElement}) => {
  const canvas = within(canvasElement);
  const addButton = canvas.getByRole("button", {name: /add label/i});
  await expect(addButton).toBeVisible();
  // the actions cluster must stay inside the 400px container
  const container = canvasElement.querySelector(".label-input") as HTMLElement;
  await expect(addButton.getBoundingClientRect().right).toBeLessThanOrEqual(container.getBoundingClientRect().right + 1);
};
