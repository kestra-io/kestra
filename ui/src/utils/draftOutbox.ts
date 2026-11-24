const PREFIX = "ks-draft-outbox"

export interface DraftKey {
    tenant: string
    namespace: string
    id: string
}

export interface QueuedDraft {
    source: string
    /** Revision the editor held when the draft was queued, to tell a server-side change apart on restore. */
    baseRevision?: number
    queuedAt: string
}

function storageKey({tenant, namespace, id}: DraftKey): string {
    return [PREFIX, tenant, namespace, id].join(":")
}

function isQueuedDraft(value: unknown): value is QueuedDraft {
    return typeof value === "object" && value !== null
        && typeof Reflect.get(value, "source") === "string"
        && typeof Reflect.get(value, "queuedAt") === "string"
}

/** Resolves false when the browser refused the write (quota, private mode), so the caller reports the failed save instead. */
export function writeQueuedDraft(key: DraftKey, draft: QueuedDraft): boolean {
    try {
        localStorage.setItem(storageKey(key), JSON.stringify(draft))
        return true
    } catch {
        return false
    }
}

export function readQueuedDraft(key: DraftKey): QueuedDraft | undefined {
    try {
        const parsed: unknown = JSON.parse(localStorage.getItem(storageKey(key)) ?? "null")
        return isQueuedDraft(parsed) ? parsed : undefined
    } catch {
        return undefined
    }
}

export function removeQueuedDraft(key: DraftKey): void {
    try {
        localStorage.removeItem(storageKey(key))
    } catch {
        return
    }
}
