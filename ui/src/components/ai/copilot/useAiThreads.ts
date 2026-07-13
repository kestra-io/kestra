/**
 * `useAiThreads` — session-level thread list for the AI Copilot v2 (Week-3 thread management).
 *
 * Owns the caller's thread LIST plus list mutations (rename / soft-delete), kept separate from
 * `useAiChat`, which owns a single active thread's turn lifecycle. Switching threads is the
 * consumer's job: call `useAiChat.loadThread(id)` on select.
 *
 * BACKEND CONTRACT (not live yet — tracked on epic kestra-ee#7909 / PR #17375):
 *   GET    …/ai/threads             → ThreadSummary[]   (EE list; OSS returns the single session)
 *   PATCH  …/ai/threads/{id}/rename   {title}           (EE)
 *   DELETE …/ai/threads/{id}                            (EE soft-delete)
 * Until they land, `list()` degrades gracefully to an empty list.
 */
import {ref} from "vue"
import {useClient} from "@kestra-io/kestra-sdk"
import {apiUrl} from "override/utils/route"
import type {ThreadSummary} from "./types"

/** Most-recent-first: prefer lastTurnAt, fall back to updatedAt. */
function byRecencyDesc(a: ThreadSummary, b: ThreadSummary): number {
    return (b.lastTurnAt ?? b.updatedAt ?? "").localeCompare(a.lastTurnAt ?? a.updatedAt ?? "")
}

export function useAiThreads() {
    const client = useClient()

    const threads = ref<ThreadSummary[]>([])
    const loading = ref(false)
    /** True when the last list() failed (e.g. endpoint not available yet). */
    const error = ref(false)

    const base = () => `${apiUrl()}/ai/threads`

    /** Loads the caller's threads, most-recent first. Best-effort — empties on failure. */
    async function list(): Promise<void> {
        loading.value = true
        error.value = false
        try {
            const {data} = await client.get<ThreadSummary[]>(base())
            threads.value = [...(data ?? [])].sort(byRecencyDesc)
        } catch {
            error.value = true
            threads.value = []
        } finally {
            loading.value = false
        }
    }

    /** Renames a thread and reflects the new title locally. */
    async function rename(threadId: string, title: string): Promise<void> {
        await client.patch(`${base()}/${threadId}/rename`, {title})
        const thread = threads.value.find((t) => t.uid === threadId)
        if (thread) thread.title = title
    }

    /** Soft-deletes a thread and drops it from the list. */
    async function remove(threadId: string): Promise<void> {
        await client.delete(`${base()}/${threadId}`)
        threads.value = threads.value.filter((t) => t.uid !== threadId)
    }

    return {threads, loading, error, list, rename, remove}
}
