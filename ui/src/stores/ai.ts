import {defineStore} from "pinia"
import {useClient} from "@kestra-io/kestra-sdk"
import {AiGenerationType, aiGenerationTypes} from "../utils/constants"
import {getUid} from "../utils/uid"
import {apiUrl} from "override/utils/route"

export const useAiStore = defineStore("ai", () => {
    const client = useClient()

    async function generate({
        userPrompt, 
        yaml, 
        conversationId, 
        providerId, 
        namespace, 
        tenantId,
        type,
    }: {
        userPrompt: string, 
        yaml?: string, 
        conversationId: string, 
        providerId?: string, 
        namespace?: string, 
        tenantId?: string,
        type: AiGenerationType
    }) {
        const response = await client.post(`${apiUrl()}/ai/generate/${type}`, {
            userPrompt,
            conversationId,
            providerId,
            namespace, 
            tenantId,
            ...(yaml !== undefined ? {yaml} : {}),
        }, {
            headers: {
                "X-Kestra-User-Id": getUid(),
            },
        })

        const remainingQuota = response.headers["x-kestra-ai-quota"]
        return {data: response.data, remainingQuota: remainingQuota ?? undefined}
    }

    async function generateFlow(options: Omit<Parameters<typeof generate>[0], "type">) {
        return generate({
            ...options,
            type: aiGenerationTypes.FLOW,
        })
    }

    return {generate, generateFlow}
})
