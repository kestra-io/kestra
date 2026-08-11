import ShowCase from "./ShowCase.vue";
import {vueRouter} from "storybook-vue3-router";

const meta = {
    title: "theme/ShowCase",
    component: ShowCase,
    decorators: [
        vueRouter([
            {
                path: "/",
                name: "home",
                component: {template: "<div>home</div>"}
            },
        ])
    ],
}

export default meta;

/**
 * @type {import('@storybook/vue3-vite').StoryObj<typeof ShowCase>}
 */
export const ElementPlusPlayground = {
    render: () => <ShowCase />,
}