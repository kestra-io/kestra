type PosthogCapturePayload = {
    event: string;
    properties: Record<string, any>;
    enqueuedAt: number;
};

const POSTHOG_QUEUE_MAX = 100;
const POSTHOG_QUEUE_MAX_AGE_MS = 5 * 60 * 1000; // 5 minutes

let posthogInitPromise: Promise<void> | undefined;
let posthogQueue: PosthogCapturePayload[] = [];
let posthogClient: any | undefined;
let posthogLoadPromise: Promise<any> | undefined;

async function loadPosthogClient() {
    if (posthogClient) return posthogClient;
    if (posthogLoadPromise) return posthogLoadPromise;

    posthogLoadPromise = import("posthog-js")
        .then((mod) => mod.default ?? mod)
        .then((client) => {
            posthogClient = client;
            return client;
        })
        .catch(() => undefined)
        .finally(() => {
            posthogLoadPromise = undefined;
        });

    return posthogLoadPromise;
}

function pruneQueue(now = Date.now()) {
    const cutoff = now - POSTHOG_QUEUE_MAX_AGE_MS;
    if (posthogQueue.length === 0) return;

    posthogQueue = posthogQueue.filter((item) => item.enqueuedAt >= cutoff);
}

function isLoaded(): boolean {
    return Boolean(posthogClient?.__loaded);
}

function flushQueue() {
    if (!isLoaded() || posthogQueue.length === 0) return;

    pruneQueue();
    if (posthogQueue.length === 0) return;

    const queued = posthogQueue;
    posthogQueue = [];

    for (const item of queued) {
        try {
            posthogClient.capture(item.event, item.properties);
        } catch {
            // swallow
        }
    }
}

function maybeInit(configs: Record<string, any> | undefined) {
    if (isLoaded()) {
        flushQueue();
        return;
    }

    // If configs are not loaded yet, we can't decide whether PostHog is enabled.
    if (configs === undefined) {
        return;
    }

    if (configs.isUiAnonymousUsageEnabled === false) {
        posthogQueue = [];
        return;
    }

    if (posthogInitPromise) return;

    posthogInitPromise = import("../composables/usePosthog")
        .then(({initPostHogForSetup}) => initPostHogForSetup(configs))
        .then(() => loadPosthogClient())
        .then(() => {
            flushQueue();
        })
        .catch(() => {
            // swallow
        })
        .finally(() => {
            posthogInitPromise = undefined;
        });
}

export function disablePosthog() {
    posthogQueue = [];
    if (!posthogClient) return;
    try {
        posthogClient.opt_out_capturing?.();
    } catch {
        // swallow
    }
    try {
        posthogClient.reset?.();
    } catch {
        // swallow
    }
}

export function capturePosthogEvent(
    configs: Record<string, any> | undefined,
    eventName: string,
    properties: Record<string, any>
) {
    if (configs?.isUiAnonymousUsageEnabled === false) {
        disablePosthog();
        return;
    }

    pruneQueue();

    if (!isLoaded()) {
        maybeInit(configs);

        if (posthogQueue.length >= POSTHOG_QUEUE_MAX) {
            posthogQueue.shift();
        }

        posthogQueue.push({
            event: eventName,
            properties,
            enqueuedAt: Date.now(),
        });

        return;
    }

    try {
        posthogClient.capture(eventName, properties);
    } catch {
        // swallow
    }

    flushQueue();
}
