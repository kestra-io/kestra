import {cloneDeep} from "@kestra-io/design-system"
import {capturePosthogEvent, disablePosthog} from "../posthog"
import {resolvePosthogEventName} from "./eventNaming"
import type {MiscControllerConfiguration} from "@kestra-io/kestra-sdk"
import type {PageInfo} from "../eventsRouter"

interface PosthogEventData {
    type: string;
    page?: Partial<PageInfo>;
    date?: string;
    counter?: number;
}

export function sendPosthogEvent<T extends PosthogEventData>(configs: MiscControllerConfiguration | undefined, data: T) {
    if (configs?.isUiAnonymousUsageEnabled === false) {
        disablePosthog()
        return
    }

    const type = data.type
    const finalData: Record<string, unknown> = {}
    Object.assign(finalData, cloneDeep(data))

    delete finalData.type
    delete finalData.date
    delete finalData.counter

    const eventName = type === "PAGE" ? "$pageview" : resolvePosthogEventName(data.type, finalData)

    if (type === "PAGE") {
        const origin = data.page?.origin ?? window.location.origin
        const path = data.page?.path ?? window.location.pathname
        const host = (() => {
            try {
                return new URL(origin).host
            } catch {
                return window.location.host
            }
        })()
        const fullPath = data.page?.fullPath
        const currentUrl = fullPath ? `${origin}${fullPath}` : `${origin}${path}`

        finalData.$current_url = currentUrl
        finalData.$pathname = path
        finalData.$host = host
        finalData.$title = document.title
    }

    capturePosthogEvent(configs, eventName, finalData)
}
