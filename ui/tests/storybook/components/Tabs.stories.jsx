import {ref} from "vue";
import Tabs from "../../../src/components/Tabs.vue";
import {vueRouter} from "storybook-vue3-router";

const meta = {
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

/**
 * @type {import('@storybook/vue3-vite').StoryObj<typeof ShowCase>}
 */
export const Default = {
    render: () => ({
        setup(){
            const activeTab = ref(tabs[0].name)

            function tabChanged(tab) {
                activeTab.value = tab.name
            }

            return () => <Tabs tabs={tabs} onChanged={tabChanged} embedActiveTab={activeTab.value} />
        }
    }),
}