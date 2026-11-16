import {onScopeDispose, reactive, shallowRef, toValue, watch, type MaybeRefOrGetter} from "vue"

import {countFlows, fetchKeyUsages, keyTargetId, type KeyTarget} from "./useKeyUsages"

const MAX_CONCURRENT_COUNTS = 3
const COUNT_TTL_MS = 60_000

export interface KeyUsageCount {
    status: "queued" | "loading" | "done" | "failed";
    flows: number;
    truncated: boolean;
}

interface CountEntry {
    target: KeyTarget;
    count: KeyUsageCount;
    createdAt: number;
    subscribers: number;
}

const entries = new Map<string, CountEntry>()
const queue: string[] = []
let running = 0

async function run(entry: CountEntry) {
    running++
    entry.count.status = "loading"
    try {
        const {usages, truncated} = await fetchKeyUsages(entry.target)
        entry.count.flows = countFlows(usages)
        entry.count.truncated = truncated
        entry.count.status = "done"
    } catch {
        entry.count.status = "failed"
    } finally {
        running--
        pump()
    }
}

function pump() {
    while (running < MAX_CONCURRENT_COUNTS && queue.length > 0) {
        const entry = entries.get(queue.shift()!)
        if (entry?.count.status === "queued") void run(entry)
    }
}

function isReusable(entry: CountEntry | undefined): entry is CountEntry {
    if (!entry || entry.count.status === "failed") return false
    return entry.count.status !== "done" || Date.now() - entry.createdAt < COUNT_TTL_MS
}

function acquire(target: KeyTarget): CountEntry {
    const id = keyTargetId(target)
    let entry = entries.get(id)
    if (!isReusable(entry)) {
        entry = {
            target: {...target},
            count: reactive({status: "queued", flows: 0, truncated: false}),
            createdAt: Date.now(),
            subscribers: 0,
        }
        entries.set(id, entry)
        queue.push(id)
        pump()
    }
    entry.subscribers++
    return entry
}

function release(entry: CountEntry) {
    entry.subscribers--
    if (entry.subscribers > 0 || entry.count.status !== "queued") return
    const id = keyTargetId(entry.target)
    const index = queue.indexOf(id)
    if (index >= 0) queue.splice(index, 1)
    if (entries.get(id) === entry) entries.delete(id)
}

export function resetKeyUsageCounts() {
    entries.clear()
    queue.length = 0
    running = 0
}

export function useKeyUsageCount(target: MaybeRefOrGetter<KeyTarget>) {
    const count = shallowRef<KeyUsageCount>()
    let current: CountEntry | undefined

    watch(
        () => keyTargetId(toValue(target)),
        () => {
            if (current) release(current)
            current = acquire(toValue(target))
            count.value = current.count
        },
        {immediate: true},
    )

    onScopeDispose(() => {
        if (current) release(current)
        current = undefined
    })

    return count
}
