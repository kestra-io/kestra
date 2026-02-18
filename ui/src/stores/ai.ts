import axios from "axios";
import {defineStore} from "pinia";
import {apiUrl} from "override/utils/route";
import {AiGenerationType} from "../utils/constants";

export const useAiStore = defineStore("ai", {
    actions: {
        async fetchProviders() {
            const response = await axios.get(`${apiUrl()}/ai/providers`);
            return response.data ?? [];
        },

        async generate({userPrompt, yaml, conversationId, providerId, type}: {userPrompt: string, yaml: string, conversationId: string, providerId?: string, type: AiGenerationType}) {
            const response = await axios.post(`${apiUrl()}/ai/generate/${type}`, {
                userPrompt,
                yaml,
                conversationId,
                providerId
            });

            return response.data;
        }

    }
});
