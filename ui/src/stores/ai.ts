import axios from "axios";
import {defineStore} from "pinia";
import {apiUrl} from "override/utils/route";
import {AiGenerationType} from "../utils/constants";
import {getUid} from "../utils/uid";

export const useAiStore = defineStore("ai", {
    actions: {
        async fetchProviders() {
            const response = await axios.get(`${apiUrl()}/ai/providers`);
            return response.data ?? [];
        },

        async generate({userPrompt, yaml, conversationId, providerId, type}: {userPrompt: string, yaml?: string, conversationId: string, providerId?: string, type: AiGenerationType}) {
            const response = await axios.post(`${apiUrl()}/ai/generate/${type}`, {
                userPrompt,
                conversationId,
                providerId,
                ...(yaml !== undefined ? {yaml} : {}),
            }, {
                headers: {
                    "X-Kestra-User-Id": getUid()
                }
            });

            const remainingQuota = response.headers["x-kestra-ai-quota"];
            return {data: response.data, remainingQuota: remainingQuota ?? undefined};
        },

        async generateFlow({userPrompt, yaml, conversationId, providerId, namespace, tenantId}: {userPrompt: string, yaml?: string, conversationId: string, providerId?: string, namespace?: string, tenantId?: string, type: AiGenerationType}) {
            const response = await axios.post(`${apiUrl()}/ai/generate/flow`, {
                userPrompt,
                conversationId,
                providerId,
                namespace,
                tenantId,
                ...(yaml !== undefined ? {yaml} : {}),
            }, {
                headers: {
                    "X-Kestra-User-Id": getUid()
                }
            });

            const remainingQuota = response.headers["x-kestra-ai-quota"];
            return {data: response.data, remainingQuota: remainingQuota ?? undefined};
        }

    }
});
