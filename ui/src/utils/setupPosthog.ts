import {useApiStore} from "../stores/api"
import {useMiscStore} from "../stores/misc"

export interface SetupEventData {
    type: string
    instance_id?: string
    user_id?: string
    [key: string]: unknown
}

interface UserFormData {
    firstName?: string
    lastName?: string
    username?: string
}

interface Config {
    isAnonymousUsageEnabled?: boolean
    uuid?: string
    version?: string
}

export async function initPostHogForSetup(config: Config): Promise<void> {
    try {
        if (!config?.isAnonymousUsageEnabled) return

        const apiStore = useApiStore()
        const apiConfig = await apiStore.loadConfig()
        
        if (!apiConfig?.posthog?.token || (window as any).posthog?.__loaded) return

        const uid = getUID()
        if (!uid) return

        const {default: posthog} = await import("posthog-js")

        posthog.init(apiConfig.posthog.token, {
            api_host: apiConfig.posthog.apiHost,
            ui_host: "https://eu.posthog.com",
            capture_pageview: false,
            capture_pageleave: true,
            autocapture: false,
        })

        if (!posthog.get_property("__alias")) {
            posthog.alias(apiConfig.id)
        }
    } catch (error) {
        console.error("Failed to initialize PostHog:", error)
    }
}

export function trackSetupEvent(
    eventName: string, 
    additionalData: Record<string, any>, 
    userFormData: UserFormData
): void {
    const miscStore = useMiscStore()
    const uid = getUID()
    
    if (!miscStore.configs?.isAnonymousUsageEnabled || !uid) return

    const userInfo = userFormData.firstName ? {
        user_firstname: userFormData.firstName,
        user_lastname: userFormData.lastName,
        user_email: userFormData.username
    } : {}

    const eventData: SetupEventData = {
        type: eventName,
        instance_id: miscStore.configs?.uuid,
        user_id: uid,
        ...userInfo,
        ...additionalData
    }

    useApiStore().posthogEvents(eventData)
}

const getUID = (): string | null => localStorage.getItem("uid")
