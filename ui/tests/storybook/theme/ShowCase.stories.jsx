import ShowCase from "./ShowCase.vue";

const meta = {
    title: "theme/ShowCase",
    component: ShowCase,
}

export default meta;

/**
 * @type {import('@storybook/vue3').StoryObj<typeof ShowCase>}
 */
export const ElementPlusPlayground = {
    render: () => <ShowCase />,
}