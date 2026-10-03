import {ensureUid} from "./uid"
import type {PostHog} from "posthog-js"

type PosthogConfig = {
    isUiAnonymousUsageEnabled?: boolean
    uuid?: string
    version?: string
    edition?: string
}

type PosthogCapturePayload = {
    event: string;
    properties: Record<string, unknown>;
    enqueuedAt: number;
};

const POSTHOG_QUEUE_MAX = 100
const POSTHOG_QUEUE_MAX_AGE_MS = 5 * 60 * 1000 // 5 minutes

let posthogInitPromise: Promise<void> | undefined
let posthogQueue: PosthogCapturePayload[] = []
let posthogClient: PostHog | undefined
let posthogLoadPromise: Promise<PostHog | undefined> | undefined

function isPosthogDisabled(configs: PosthogConfig | undefined){
    return configs?.isUiAnonymousUsageEnabled === false || import.meta.env.MODE === "development"
}

export function isPosthogEnabled(configs: PosthogConfig | undefined) {
    return !isPosthogDisabled(configs)
}

async function loadPosthogClient() {
    if (posthogClient) return posthogClient
    if (posthogLoadPromise) return posthogLoadPromise

    posthogLoadPromise = import("posthog-js")
        .then((mod) => mod.default ?? mod)
        .then((client) => {
            posthogClient = client
            return client
        })
        .catch(() => undefined)
        .finally(() => {
            posthogLoadPromise = undefined
        })

    return posthogLoadPromise
}

function pruneQueue(now = Date.now()) {
    const cutoff = now - POSTHOG_QUEUE_MAX_AGE_MS
    if (posthogQueue.length === 0) return

    posthogQueue = posthogQueue.filter((item) => item.enqueuedAt >= cutoff)
}

function isLoaded(): boolean {
    return Boolean(posthogClient?.__loaded)
}

function flushQueue() {
    if (!isLoaded() || posthogQueue.length === 0) return

    pruneQueue()
    if (posthogQueue.length === 0) return

    const queued = posthogQueue
    posthogQueue = []

    for (const item of queued) {
        try {
            posthogClient.capture(item.event, item.properties)
        } catch {
            // swallow
        }
    }
}

async function ensurePosthogClient(configs: PosthogConfig | undefined) {
    if (isLoaded()) return posthogClient
    if (configs === undefined) return undefined
    if (isPosthogDisabled(configs)) {
        posthogQueue = []
        return undefined
    }

    if (!posthogInitPromise) {
        posthogInitPromise = import("../composables/usePosthog")
            .then(({initPostHogForSetup}) => initPostHogForSetup(configs))
            .then(() => loadPosthogClient())
            .then(() => {
                flushQueue()
            })
            .catch(() => undefined)
            .finally(() => {
                posthogInitPromise = undefined
            })
    }

    try {
        await posthogInitPromise
    } catch {
        return undefined
    }

    return isLoaded() ? posthogClient : undefined
}

export function disablePosthog() {
    posthogQueue = []
    if (!posthogClient) return
    try {
        posthogClient.opt_out_capturing?.()
    } catch {
        // swallow
    }
    try {
        posthogClient.reset?.()
    } catch {
        // swallow
    }
}

export function capturePosthogEvent(
    configs: PosthogConfig | undefined,
eventName: string,
properties: Record<string, unknown>,
) {
    if (isPosthogDisabled(configs)) {
        disablePosthog()
        return
    }

    pruneQueue()

    if (!isLoaded()) {
        void ensurePosthogClient(configs)

        if (posthogQueue.length >= POSTHOG_QUEUE_MAX) {
            posthogQueue.shift()
        }

        posthogQueue.push({
            event: eventName,
            properties,
            enqueuedAt: Date.now(),
        })

        return
    }

    try {
        posthogClient.capture(eventName, properties)
    } catch {
        // swallow
    }

    flushQueue()
}

export async function identifyPosthogUser(
    configs: PosthogConfig | undefined,
properties: Record<string, unknown>,
) {
    if (isPosthogDisabled(configs)) {
        disablePosthog()
        return
    }

    if (!properties || Object.keys(properties).length === 0) return

    const client = await ensurePosthogClient(configs)
    if (!client?.identify) return

    try {
        client.identify(ensureUid(), properties)
    } catch {
        // swallow
    }
}

export async function initPosthogIfEnabled(configs: PosthogConfig | undefined) {
    if (isPosthogDisabled(configs)) {
        disablePosthog()
        return
    }

    await ensurePosthogClient(configs)
}
