import {defineStore} from "pinia";
import {AI} from "../generated/kestra-api/ks-sdk.gen";

export const useAiStore = defineStore("ai", {
    actions: {
        async generateFlow({userPrompt, flowYaml, conversationId}: {userPrompt: string, flowYaml: string, conversationId: string}) {
            const response = await AI.generateFlow({
                flowGenerationPrompt:{
                    userPrompt,
                    flowYaml,
                    conversationId
                }
            });

            return response.data;
        }
    }
});
