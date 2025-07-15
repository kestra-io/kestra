import type {Module} from "vuex";

import axios from "axios";

import {apiUrl} from "override/utils/route";

const ai: Module<any, unknown> = {
    namespaced: true,
    actions: {
        async generateFlow(_, {userPrompt, flowYaml}: {userPrompt: string, flowYaml: string}) {
            return (await axios.post(`${apiUrl(this)}/ai/generate/flow`, {
                userPrompt,
                flowYaml
            })).data;
        }
    },
};

export default ai;
