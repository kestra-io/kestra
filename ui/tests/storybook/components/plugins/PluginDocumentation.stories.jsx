import PluginDocumentation from "../../../../src/components/plugins/PluginDocumentation.vue";
import {useAxios} from "../../../../src/utils/axios";

export default {
    title: "Components/Plugins/PluginDocumentation",
    component: PluginDocumentation,
    argTypes: {
        overrideIntro: {control: "text"},
    },
};

const Template = (args) => ({
    setup() {
        const axios = useAxios()
        axios.get = () =>{
                return  Promise.resolve({data: []})
            }

        return () => <PluginDocumentation {...args} />
    }
});

export const Default = Template.bind({});
Default.args = {
    overrideIntro: "This is an overridden intro content.",
};
