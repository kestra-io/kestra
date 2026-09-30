import type {Meta, StoryObj} from "@storybook/vue3-vite";
import {ref} from "vue";
import Tabs, {type Tab} from "../../../src/components/Tabs.vue";
import {vueRouter} from "storybook-vue3-router";

const meta: Meta<typeof Tabs> = {
    title: "components/Tabs",
    component: Tabs,
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

const tabs = [
    {
        title: "Tab 1",
        name: "first",
    },
    {
        title: "Tab 2",
        name: "second",
    },
    {
        title: "Tab 3",
        name: "third",
    },
]

export const Default: StoryObj<typeof Tabs> = {
    render: () => ({
        setup(){
            const activeTab = ref<string | undefined>(tabs[0].name)

            function tabChanged(tab: Tab) {
                activeTab.value = tab.name
            }

            return () => <Tabs tabs={tabs} onChanged={tabChanged} embedActiveTab={activeTab.value} />
        }
    }),
}
