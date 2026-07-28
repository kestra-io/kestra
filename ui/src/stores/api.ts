import axios from "axios"
import cloneDeep from "lodash/cloneDeep"
import {defineStore} from "pinia"
import {ref} from "vue"
import {useMiscStore} from "override/stores/misc"
import {capturePosthogEvent, disablePosthog} from "../utils/posthog"
import {ensureUid} from "../utils/uid"
import {PendingEventsBuffer} from "../utils/analytics/pendingEvents"
import {resolvePosthogEventName} from "../utils/analytics/eventNaming"

export const API_URL = "https://api.kestra.io"

interface Feed {
    id: string;
    publicationDate: string;
    href?: string;
    title: string;
    description: string;
    image?: string;
    content: string;
    link: string;
}

interface FeedResponse {
    feeds: Feed[];
    version: string;
}

interface EventData {
    type: string;
    page?: {
        origin?: string;
        path?: string;
        fullPath?: string;
        [key: string]: any;
    };
    [key: string]: any;
}

interface EventsOptions {
    posthog?: boolean;
}

type Configs = Record<string, any>;

let counter = 0

const pendingEvents = new PendingEventsBuffer<EventData, EventsOptions>({
    maxItems: 50,
    maxAgeMs: 2 * 60 * 1000, // 2 minutes
})

function analyticsDisabled(configs: Configs): boolean {
    return configs["isAnonymousUsageEnabled"] === false
}

function buildEventPayload(data: EventData, configs: Configs, uid: string) {
    const additionalData = {
        iid: configs.uuid,
        uid,
        date: new Date().toISOString(),
        counter: counter++,
    }

    const mergeData = {
        ...data,
        ...additionalData,
    }

    const backendData: Partial<EventData> = cloneDeep(mergeData)
    if (backendData.page) {
        delete backendData.page.origin
        delete backendData.page.path
        delete backendData.page.fullPath
    }
    delete (backendData as any).$referrer
    delete (backendData as any).$referring_domain

    return {mergeData, backendData}
}

export const useApiStore = defineStore("api", () => {
    const feeds = ref<Feed[]>([])

    async function loadFeeds(options: { iid: string; uid: string; version: string }) {
        const response = await axios.get<FeedResponse>(`${API_URL}/v1/feeds`, {
            withCredentials: true,
            params: {
                iid: options.iid,
                uid: options.uid,
                version: options.version,
            },
        })

        feeds.value = response.data.feeds

        return response.data
    }

    async function loadConfig() {
        const response = await axios.get(`${API_URL}/v1/config`, {
            withCredentials: true,
        })

        return response.data
    }

    async function flushQueuedEvents() {
        const miscStore = useMiscStore()
        const configs = miscStore.configs

        // Can't decide yet.
        if (configs === undefined) return

        // Analytics disabled: drop queued events.
        if (analyticsDisabled(configs)) {
            pendingEvents.clear()
            return
        }

        // Ensure uid exists now that we know analytics is enabled.
        const uid = ensureUid()

        const toFlush = pendingEvents.drain()
        for (const item of toFlush) {
            try {
                await sendEventNow(item.data, item.options, configs, uid)
            } catch {
                // Best-effort flush: keep draining even if a send fails.
            }
        }
    }

    async function events(data: EventData, options: EventsOptions = {}) {
        const miscStore = useMiscStore()
        const configs = miscStore.configs

        // If configs aren't ready yet, buffer and replay later.
        if (configs === undefined) {
            pendingEvents.enqueue(data, options)
            return
        }

        if (analyticsDisabled(configs)) {
            pendingEvents.clear()
            return
        }

        const uid = ensureUid()

        return sendEventNow(data, options, configs, uid)
    }

    async function sendEventNow(data: EventData, options: EventsOptions, configs: Configs, uid: string) {
        const {mergeData, backendData} = buildEventPayload(data, configs, uid)

        if (options.posthog !== false) {
            posthogEvents(mergeData)
        }

        // Configs are loaded: flush any buffered events best-effort.
        if (pendingEvents.length > 0) {
            void flushQueuedEvents()
        }

        return axios.post(`${API_URL}/v1/reports/events`, backendData, {
            withCredentials: true,
        })
    }

    function posthogEvents(data: EventData & { date?: string; counter?: number }) {
        const miscStore = useMiscStore()
        const configs = miscStore.configs
        if (configs?.isUiAnonymousUsageEnabled === false) {
            disablePosthog()
            return
        }

        const type = data.type
        const finalData: Partial<EventData> = cloneDeep(data)

        delete finalData.type
        delete finalData.date
        delete finalData.counter

        const eventName = type === "PAGE" ? "$pageview" : resolvePosthogEventName(data.type, finalData as Record<string, any>)

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
            const currentUrl = fullPath ? `${origin}${fullPath}` : `${origin}${path}`;

            (finalData as any).$current_url = currentUrl;
            (finalData as any).$pathname = path;
            (finalData as any).$host = host;
            (finalData as any).$title = document.title
        }

        capturePosthogEvent(configs, eventName, finalData as Record<string, any>)
    }

    async function pluginsInformation() {
        return axios.get<{byPlugin: Record<string, {lastReleasedAt?: string; usageCount?: number}>}>(
            `${API_URL}/v1/plugins/pluginsInformation?icons=false`,
            {withCredentials: true},
        )
    }

    return {
        feeds,
        loadFeeds,
        loadConfig,
        flushQueuedEvents,
        events,
        sendEventNow,
        posthogEvents,
        pluginsInformation,
    }
})
