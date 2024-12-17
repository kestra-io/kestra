import { ref, getCurrentInstance } from "vue";
import Tabs from "./Tabs.vue";
import { setup } from "@storybook/vue3";

const meta = {
    title: "components/Tabs",
    component: Tabs,
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
 * @type {import('@storybook/vue3').StoryObj<typeof ShowCase>}
 */
export const Default = {
    render: () => ({
        setup(){
            // mock app router
            const app = getCurrentInstance()?.appContext.config.globalProperties
            if(app){
                app.$router = {}
                app.$route = {
                    params: {tab: "first"}
                }
            }

            const activeTab = ref(tabs[0].name)

            function tabChanged(tab) {
                activeTab.value = tab.name
            }

            return () => <Tabs tabs={tabs} onChanged={tabChanged} embedActiveTab={activeTab.value} />
        }
    }),
}