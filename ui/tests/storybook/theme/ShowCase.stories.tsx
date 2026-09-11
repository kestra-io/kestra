import type {Meta, StoryObj} from "@storybook/vue3-vite";
import ShowCase from "./ShowCase.vue";
import {vueRouter} from "storybook-vue3-router";

const meta: Meta<typeof ShowCase> = {
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

export const ElementPlusPlayground: StoryObj<typeof ShowCase> = {
    render: () => <ShowCase />,
}
