import {computed, onScopeDispose, ref, watch, type Ref} from "vue"
import {useIntervalFn} from "@vueuse/core"
import type {LogEntry} from "@kestra-io/kestra-sdk"
import {LoopIterationError, type LoopRoot} from "../utils/loopIterations"
import {
    collectFailedTargets,
    collectScopedTargets,
    createChainCache,
    logUrlReserve,
    searchMergedLogs,
    type ChainCache,
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
    q?: Ref<string | undefined>;
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
    let cache: ChainCache = createChainCache()
    let cacheRootId: string | undefined

    const hasMore = computed(() => cursorMode.value ? Boolean(nextCursor.value) : lines.value.length < total.value)

    const kinds = () => [options.root.value?.kind ?? "NORMAL", "LOOP"].filter((kind, index, all) => all.indexOf(kind) === index)

    const search = (executionIds: string[], pageNumber: number, cursor?: string) => searchMergedLogs({
        executionIds,
        kinds: kinds(),
        levelParams: options.levelParams.value,
        q: options.q?.value,
        page: pageNumber,
        cursor,
    })

    async function load(keepExtent = false) {
        const root = options.root.value
        if (!root?.id) return
        if (cacheRootId !== root.id) {
            cache = createChainCache()
            cacheRootId = root.id
        }
        const current = ++seq
        loading.value = true
        try {
            const collectOptions = {reserve: logUrlReserve({levelParams: options.levelParams.value, kinds: kinds(), q: options.q?.value}), cache}
            const resolved = options.scope.value.length
                ? await collectScopedTargets(root, options.scope.value, undefined, collectOptions)
                : await collectFailedTargets(root, undefined, collectOptions)

            const pagesToLoad = keepExtent ? page : 1
            let collected: LogEntry[] = []
            let cursor: string | undefined
            let result = await search(resolved.executionIds, 1)
            let pagesLoaded = 1
            collected = collected.concat(result.results)
            cursor = result.nextCursor
            while (pagesLoaded < pagesToLoad && (result.cursorMode ? Boolean(cursor) : collected.length < result.total)) {
                result = await search(resolved.executionIds, pagesLoaded + 1, cursor)
                collected = collected.concat(result.results)
                cursor = result.nextCursor
                pagesLoaded++
            }
            if (current !== seq) return
            targets.value = resolved
            lines.value = collected
            total.value = result.total
            nextCursor.value = cursor
            cursorMode.value = result.cursorMode
            page = pagesLoaded
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
            const result = await search(targets.value.executionIds, page + 1, nextCursor.value)
            if (current !== seq) return
            page += 1
            lines.value = lines.value.concat(result.results)
            total.value = result.total
            nextCursor.value = result.nextCursor
        } catch {
            failure.value = "unknown"
        } finally {
            loadingMore.value = false
        }
    }

    const scopeKey = computed(() => options.scope.value.map(({taskId, number}) => `${taskId}:${number}`).join(","))
    const searchKey = computed(() => `${options.root.value?.id ?? ""}|${scopeKey.value}|${JSON.stringify(options.levelParams.value)}|${options.q?.value ?? ""}`)
    watch(searchKey, () => load(), {immediate: true})

    const refreshTimer = useIntervalFn(() => {
        if (!loading.value) load(true)
    }, AUTO_REFRESH_MS, {immediate: false})
    watch(options.running, (running, wasRunning) => {
        if (running) {
            refreshTimer.resume()
            return
        }
        refreshTimer.pause()
        if (wasRunning) load(true)
    }, {immediate: true})
    onScopeDispose(() => {
        seq++
        refreshTimer.pause()
    })

    return {targets, lines, total, loading, loadingMore, loaded, failure, hasMore, refresh: () => load(true), loadMore}
}
