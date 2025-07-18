import axios from "axios";
import {defineStore} from "pinia";
import {apiUrl} from "override/utils/route";

export const useAiStore = defineStore("ai", {
    actions: {
        async generateFlow({userPrompt, flowYaml}: {userPrompt: string, flowYaml: string}) {
            const response = await axios.post(`${apiUrl(this.vuexStore)}/ai/generate/flow`, {
                userPrompt,
                flowYaml
            });
            
            return response.data;
        }
    }
});
