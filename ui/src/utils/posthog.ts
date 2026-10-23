import type {AppConfig} from "vue"
import {ensureUid} from "./uid"

type PosthogCapturePayload = {
    event: string;
    properties: Record<string, any>;
    enqueuedAt: number;
};

const POSTHOG_QUEUE_MAX = 100
const POSTHOG_QUEUE_MAX_AGE_MS = 5 * 60 * 1000 // 5 minutes
const EXCEPTION_REPEAT_WINDOW_MS = 60 * 1000

const recentExceptions = new Map<string, number>()

let posthogInitPromise: Promise<void> | undefined
let posthogQueue: PosthogCapturePayload[] = []
let posthogClient: any | undefined
let posthogLoadPromise: Promise<any> | undefined

function isPosthogDisabled(configs: Record<string, any> | undefined) {
    return configs?.isUiAnonymousUsageEnabled === false || import.meta.env.MODE === "development"
}

export function isPosthogEnabled(configs?: Record<string, any>) {
    if (!configs) {
        return false
    }
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

async function ensurePosthogClient(configs: Record<string, any> | undefined) {
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
    configs: Record<string, any> | undefined,
    eventName: string,
    properties: Record<string, any>,
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

function exceptionKey(error: unknown, properties?: Record<string, unknown>) {
    const description = error instanceof Error ? `${error.name}: ${error.message}` : String(error)
    return `${description}|${String(properties?.info ?? "")}`
}

function isRepeatedException(key: string, now = Date.now()) {
    const lastCapturedAt = recentExceptions.get(key)
    if (lastCapturedAt !== undefined && now - lastCapturedAt < EXCEPTION_REPEAT_WINDOW_MS) return true

    if (recentExceptions.size >= POSTHOG_QUEUE_MAX) recentExceptions.clear()
    recentExceptions.set(key, now)
    return false
}

export function capturePosthogException(
    configs: Record<string, unknown> | undefined,
    error: unknown,
    properties?: Record<string, unknown>,
) {
    if (isPosthogDisabled(configs)) return
    if (isRepeatedException(exceptionKey(error, properties))) return

    void ensurePosthogClient(configs)
        .then((client) => client?.captureException?.(error, properties))
        .catch(() => undefined)
}

export function createPosthogVueErrorHandler(
    getConfigs: () => Record<string, unknown> | undefined,
): NonNullable<AppConfig["errorHandler"]> {
    return (error, _instance, info) => {
        console.error(error, info)
        capturePosthogException(getConfigs(), error, {handler: "vue", info})
    }
}

export async function identifyPosthogUser(
    configs: Record<string, any> | undefined,
    properties: Record<string, any>,
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

export async function initPosthogIfEnabled(configs: Record<string, any> | undefined) {
    if (isPosthogDisabled(configs)) {
        disablePosthog()
        return
    }

    await ensurePosthogClient(configs)
}
