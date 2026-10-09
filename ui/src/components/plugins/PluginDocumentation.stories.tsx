import type {Meta, StoryFn} from "@storybook/vue3-vite";
import PluginDocumentation from "./PluginDocumentation.vue";
import dashboardIntro from "../../assets/docs/dashboard_home.md?raw"
import {setMockClient, type AxiosLikeClient} from "@kestra-io/kestra-sdk"
import {mockResponse} from "../../../.storybook/apiMock"

export default {
    title: "Components/Plugins/PluginDocumentation",
    component: PluginDocumentation,
    argTypes: {
        overrideIntro: {control: "text"},
    },
} as Meta<typeof PluginDocumentation>;

const Template: StoryFn<typeof PluginDocumentation> = (args) => ({
    setup() {
        const axios: Partial<AxiosLikeClient> = {}
        axios.get = async <T,>() => mockResponse<T>([])
        setMockClient(axios);

        return () => <PluginDocumentation {...args} />
    }
});

export const Default = Template.bind({});
Default.args = {};

export const WithOverrideIntro = Template.bind({});
WithOverrideIntro.args = {
    overrideIntro: dashboardIntro,
};
