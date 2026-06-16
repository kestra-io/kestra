const SYSTEM_PREFIX = "system."
const SYSTEM_FROM_KEY = `${SYSTEM_PREFIX}from`
const ALLOWED_USER_SYSTEM_LABEL_KEYS = new Set([`${SYSTEM_PREFIX}correlationId`])
const SYSTEM_FROM_UI_LABEL = `${SYSTEM_FROM_KEY}:ui`

export interface ExecutionLabelInput {
    key: string | null
    value: string | null
}

/**
 * Whether a label key is a user-forbidden system label at execution creation time.
 * Only {@code system.correlationId} may be set manually; {@code system.from} is injected by the UI.
 */
export function isForbiddenUserSystemLabel(key: string): boolean {
    return key.startsWith(SYSTEM_PREFIX) && !ALLOWED_USER_SYSTEM_LABEL_KEYS.has(key)
}

export function hasForbiddenUserSystemLabels(labels: ExecutionLabelInput[]): boolean {
    return labels.some(label => label.key && label.value && isForbiddenUserSystemLabel(label.key))
}

/**
 * Builds execution label query strings for the trigger API, appending {@code system.from:ui} when absent.
 */
export function buildExecutionLabelStrings(labels: ExecutionLabelInput[]): string[] {
    const labelStrings = labels
        .filter((label): label is {key: string; value: string} => Boolean(label.key && label.value))
        .map(label => `${label.key}:${label.value}`)

    if (!labelStrings.some(label => label.startsWith(`${SYSTEM_FROM_KEY}:`))) {
        labelStrings.push(SYSTEM_FROM_UI_LABEL)
    }

    return [...new Set(labelStrings)]
}
