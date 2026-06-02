import {defineStore} from "pinia"
import * as AiApi from "@kestra-io/kestra-sdk/ai"
import {useClient} from "@kestra-io/kestra-sdk"
import {AiGenerationType, aiGenerationTypes} from "../utils/constants"
import {getUid} from "../utils/uid"
import {ref} from "vue"

export const useAiStore = defineStore("ai", () => {
    const client = useClient()
    const remainingQuota = ref("")

    client.interceptors.response.use((response) => {
        if (response.headers["x-kestra-ai-quota"] !== undefined) {
            remainingQuota.value = response.headers["x-kestra-ai-quota"]
        }
        return response
    })

    async function fetchProviders() {
        return await AiApi.providers()
    }

    async function generate({
        userPrompt, 
        yaml, 
        conversationId, 
        providerId, 
        type,
    }: {
            userPrompt: string, 
            yaml?: string, 
            conversationId: string, 
            providerId?: string, 
            type: AiGenerationType
    }) {
        const methodMap = {
            [aiGenerationTypes.FLOW]: AiApi.generateFlow,
            [aiGenerationTypes.APP]: AiApi.generateApp,
            [aiGenerationTypes.DASHBOARD]: AiApi.generateDashboard,
            [aiGenerationTypes.TEST]: AiApi.generateTestSuite,
        } as const

        const response = await methodMap[type]({
            userPrompt,
            conversationId,
            providerId,
            ...(yaml !== undefined ? {yaml} : {}),
            
        },{
            headers: {
                "X-Kestra-User-Id": getUid(),
            },
            client: client,
        })

        return {data: response, remainingQuota: remainingQuota.value ?? undefined}
    }

    async function generateFlow(options: {
        userPrompt: string, 
        yaml?: string, 
        conversationId: string, 
        providerId?: string, 
        namespace?: string, 
        tenantId?: string
    }) {
        return generate({
            ...options,
            type: aiGenerationTypes.FLOW,
        })
    }

    return {fetchProviders, generate, generateFlow}
})
