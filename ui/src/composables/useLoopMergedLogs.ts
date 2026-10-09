import {computed, onScopeDispose, ref, watch, type Ref} from "vue"
import {useIntervalFn} from "@vueuse/core"
import type {LogEntry} from "@kestra-io/kestra-sdk"
import {LoopIterationError, type LoopRoot} from "../utils/loopIterations"
import {
    collectFailedTargets,
    collectScopedTargets,
    searchMergedLogs,
    type LoopLogTargets,
} from "../utils/loopLogScope"
import type {LoopScopeEntry} from "../utils/loopScope"

export const AUTO_REFRESH_MS = 10_000

export type MergedLogsFailure = "forbidden" | "not-found" | "unknown"

export interface MergedLogsOptions {
    root: Ref<(LoopRoot & {kind?: string}) | undefined>;
    scope: Ref<LoopScopeEntry[]>;
    levelParams: Ref<Record<string, string>>;
    running: Ref<boolean>;
}

export function useLoopMergedLogs(options: MergedLogsOptions) {
    const targets = ref<LoopLogTargets>()
    const lines = ref<LogEntry[]>([])
    const total = ref(0)
    const loading = ref(false)
    const loadingMore = ref(false)
    const loaded = ref(false)
    const failure = ref<MergedLogsFailure>()
    const nextCursor = ref<string>()
    const cursorMode = ref(false)
    let page = 1
    let seq = 0

    const hasMore = computed(() => cursorMode.value ? Boolean(nextCursor.value) : lines.value.length < total.value)

    const kinds = () => [options.root.value?.kind ?? "NORMAL", "LOOP"].filter((kind, index, all) => all.indexOf(kind) === index)

    async function load() {
        const root = options.root.value
        if (!root?.id) return
        const current = ++seq
        loading.value = true
        try {
            const resolved = options.scope.value.length
                ? await collectScopedTargets(root, options.scope.value)
                : await collectFailedTargets(root)
            const result = await searchMergedLogs({
                executionIds: resolved.executionIds,
                kinds: kinds(),
                levelParams: options.levelParams.value,
                page: 1,
            })
            if (current !== seq) return
            targets.value = resolved
            lines.value = result.results
            total.value = result.total
            nextCursor.value = result.nextCursor
            cursorMode.value = result.cursorMode
            page = 1
            failure.value = undefined
        } catch (error) {
            if (current !== seq) return
            failure.value = error instanceof LoopIterationError ? error.failure : "unknown"
        } finally {
            if (current === seq) {
                loading.value = false
                loaded.value = true
            }
        }
    }

    async function loadMore() {
        if (!hasMore.value || loadingMore.value || loading.value || !targets.value) return
        const current = seq
        loadingMore.value = true
        try {
            const result = await searchMergedLogs({
                executionIds: targets.value.executionIds,
                kinds: kinds(),
                levelParams: options.levelParams.value,
                page: page + 1,
                cursor: nextCursor.value,
            })
            if (current !== seq) return
            page += 1
            lines.value = lines.value.concat(result.results)
            nextCursor.value = result.nextCursor
        } catch {
            failure.value = "unknown"
        } finally {
            loadingMore.value = false
        }
    }

    const scopeKey = computed(() => options.scope.value.map(({taskId, number}) => `${taskId}:${number}`).join(","))
    const searchKey = computed(() => `${options.root.value?.id ?? ""}|${scopeKey.value}|${JSON.stringify(options.levelParams.value)}`)
    watch(searchKey, load, {immediate: true})

    const refreshTimer = useIntervalFn(() => {
        if (!loading.value) load()
    }, AUTO_REFRESH_MS, {immediate: false})
    watch(options.running, (running) => running ? refreshTimer.resume() : refreshTimer.pause(), {immediate: true})
    onScopeDispose(() => {
        seq++
        refreshTimer.pause()
    })

    return {targets, lines, total, loading, loadingMore, loaded, failure, hasMore, refresh: load, loadMore}
}
