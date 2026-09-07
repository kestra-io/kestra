import Labels from "../../../../src/components/layout/Labels.vue";
import {Meta, StoryFn} from "@storybook/vue3";
import {expect, userEvent, waitFor} from "storybook/test";

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

const openedPopover = async () => waitFor(() => {
  const content = document.querySelector("[data-test=\"labels-overflow-content\"]") as HTMLElement;
  expect(content).not.toBeNull();
  return content;
});

export const OverflowOpensOnClick = Template.bind({});
OverflowOpensOnClick.args = {labels: LABELS, max: 3};
OverflowOpensOnClick.play = async ({canvasElement}) => {
  const overflow = canvasElement.querySelector("[data-test=\"labels-overflow\"]") as HTMLElement;

  await userEvent.click(overflow);
  const content = await openedPopover();

  await expect([...content.querySelectorAll("[data-test=\"label\"]")].map((el) => el.textContent?.trim()))
    .toEqual(["jira:KESTRA-18825", "owner:core"]);
};

// The chip is a real button so it activates on Enter, which is the only way in without a mouse.
export const OverflowOpensOnEnter = Template.bind({});
OverflowOpensOnEnter.args = {labels: LABELS, max: 3};
OverflowOpensOnEnter.play = async ({canvasElement}) => {
  const overflow = canvasElement.querySelector("[data-test=\"labels-overflow\"]") as HTMLElement;

  overflow.focus();
  await expect(document.activeElement).toBe(overflow);

  await userEvent.keyboard("{Enter}");
  const content = await openedPopover();

  // Scoped to the popover, the long value wraps instead of bleeding out of it.
  const long = [...content.querySelectorAll("[data-test=\"label\"]")].at(-1) as HTMLElement;
  await expect(long.getBoundingClientRect().right).toBeLessThanOrEqual(content.getBoundingClientRect().right);
};
