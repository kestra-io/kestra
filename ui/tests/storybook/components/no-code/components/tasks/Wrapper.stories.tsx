import Wrapper from "../../../../../../src/components/no-code/components/tasks/Wrapper.vue";
import {Meta, StoryObj} from "@storybook/vue3-vite";

const meta: Meta<typeof Wrapper> = {
    title: "Components/NoCode/Wrapper",
    component: Wrapper,
};

export default meta;

type Story = StoryObj<typeof Wrapper>;

export const Default: Story = {
    render: () => ({
        setup() {
            return () => <Wrapper>
                {{tasks: () => <p>Content inside the wrapper</p>}}
            </Wrapper>
        },
    }),
};

export const Merged: Story = {
    render: () => ({
        setup() {
            return () => <Wrapper merge>
                {{tasks: () => <p>Content inside merged wrapper (no border)</p>}}
            </Wrapper>
        },
    }),
};

export const Transparent: Story = {
    render: () => ({
        setup() {
            return () => <Wrapper transparent>
                {{tasks: () => <p>Content inside transparent wrapper (no wrapper div)</p>}}
            </Wrapper>
        },
    }),
};
