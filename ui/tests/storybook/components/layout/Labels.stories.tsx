import Labels from "../../../../src/components/layout/Labels.vue";
import {Meta, StoryFn} from "@storybook/vue3";
import {expect} from "storybook/test";

export default {
  title: "Components/Layout/Labels",
  component: Labels,
} as Meta<typeof Labels>;

const Template: StoryFn<typeof Labels> = (args) => ({
  setup() {
    return () => <Labels {...args} />;
  }
});

const LABELS = [
  {key: "team", value: "platform-engineering"},
  {key: "environment", value: "production"},
  {key: "region", value: "europe-west1"},
  {key: "jira", value: "KESTRA-18825"},
  {key: "owner", value: "core"},
];

const rendered = (root: ParentNode) => ({
  chips: [...root.querySelectorAll("[data-test=\"label\"]")].map((el) => el.textContent?.trim()),
  overflow: (root.querySelector("[data-test=\"labels-overflow\"]") as HTMLElement | null)?.textContent?.trim(),
});

export const Capped = Template.bind({});
Capped.args = {labels: LABELS, max: 3};
Capped.play = async ({canvasElement}) => {
  const {chips, overflow} = rendered(canvasElement);

  await expect(chips).toEqual(["team:platform-engineering", "environment:production", "region:europe-west1"]);
  await expect(overflow).toBe("+2");
};

export const OneOverTheCap = Template.bind({});
OneOverTheCap.args = {labels: LABELS.slice(0, 4), max: 3};
OneOverTheCap.play = async ({canvasElement}) => {
  const {chips, overflow} = rendered(canvasElement);

  await expect(chips.length).toBe(4);
  await expect(overflow).toBeUndefined();
};

export const Uncapped = Template.bind({});
Uncapped.args = {labels: LABELS};
Uncapped.play = async ({canvasElement}) => {
  const {chips, overflow} = rendered(canvasElement);

  await expect(chips.length).toBe(LABELS.length);
  await expect(overflow).toBeUndefined();
};
