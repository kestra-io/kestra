import axios from "axios";
import {defineStore} from "pinia";
import {apiUrl} from "override/utils/route";

export const useAiStore = defineStore("ai", {
    actions: {
        async fetchProviders() {
            const response = await axios.get(`${apiUrl()}/ai/providers`);
            return response.data ?? [];
        },

        async generateFlow({userPrompt, flowYaml, conversationId, providerId}: {userPrompt: string, flowYaml: string, conversationId: string, providerId?: string}) {
            const response = await axios.post(`${apiUrl()}/ai/generate/flow`, {
                userPrompt,
                flowYaml,
                conversationId,
                providerId
            });

            return response.data;
        }
    }
});
