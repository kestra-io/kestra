import axios from "axios";
import {defineStore} from "pinia";
import {apiUrl} from "override/utils/route";

export const useAiStore = defineStore("ai", {
    actions: {
        async generateFlow({userPrompt, flowYaml, conversationId}: {userPrompt: string, flowYaml: string, conversationId: string}) {
            const response = await axios.post(`${apiUrl()}/ai/generate/flow`, {
                userPrompt,
                flowYaml,
                conversationId
            });

            return response.data;
        }
    }
});
