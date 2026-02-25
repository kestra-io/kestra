import {useApiStore} from "../stores/api"
import {useMiscStore} from "override/stores/misc"
import {ensureUid, getUid} from "../utils/uid"

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
    isUiAnonymousUsageEnabled?: boolean
    uuid?: string
    version?: string
    edition?: string
}

function statsGlobalData(config: Config, uid: string): any {
    return {
        from: "APP",
        iid: config.uuid,
        uid: uid,
        app: {
            version: config.version,
            type: config.edition
        }
    }
}

const SURVEY_HOOKS_FLAG = "__kestra_posthog_survey_hooks_installed"

function installSurveyHooksOnce() {
    if ((window as any)[SURVEY_HOOKS_FLAG]) return
    (window as any)[SURVEY_HOOKS_FLAG] = true

    let surveyVisible = false
    window.addEventListener("PHSurveyShown", () => {
        surveyVisible = true
    })

    window.addEventListener("PHSurveyClosed", () => {
        surveyVisible = false
    })

    window.addEventListener("KestraRouterAfterEach", () => {
        if (surveyVisible) {
            window.dispatchEvent(new Event("PHSurveyClosed"))
            surveyVisible = false
        }
    })
}

export async function initPostHogForSetup(config: Config): Promise<void> {
    try {
        const uid = ensureUid()

        const {default: posthog} = await import("posthog-js")

        // PostHog can already be initialized (e.g. user logs out then logs back in without a full page refresh).
        // In that case we don't need to init again.
        if ((posthog as any)?.__loaded) {
            installSurveyHooksOnce()
            return
        }

        const apiStore = useApiStore()
        const apiConfig = await apiStore.loadConfig()

        if (!apiConfig?.posthog?.token) return

        posthog.init(apiConfig.posthog.token, {
            api_host: apiConfig.posthog.apiHost,
            ui_host: "https://eu.posthog.com",
            capture_pageview: false,
            capture_pageleave: true,
            autocapture: false,
        })

        posthog.register_for_session(statsGlobalData(config, uid));

        if (!posthog.get_property("__alias")) {
            posthog.alias(apiConfig.id)
        }

        installSurveyHooksOnce()
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
    const uid = getUid()

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
