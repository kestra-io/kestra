import PluginDocumentation from "../../../../src/components/plugins/PluginDocumentation.vue";
import dashboardIntro from "../../../../src/assets/docs/dashboard_home.md?raw"
import {setMockClient} from "@kestra-io/kestra-sdk"

export default {
    title: "Components/Plugins/PluginDocumentation",
    component: PluginDocumentation,
    argTypes: {
        overrideIntro: {control: "text"},
    },
};

const Template = (args) => ({
    setup() {
        const axios = {}
        axios.get = () =>{
                return  Promise.resolve({data: []})
            }
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
