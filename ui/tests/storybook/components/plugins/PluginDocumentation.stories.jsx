import PluginDocumentation from "../../../../src/components/plugins/PluginDocumentation.vue";
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
Default.args = {
    overrideIntro: "This is an overridden intro content.",
};
