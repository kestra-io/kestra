import {computed, onScopeDispose, ref, watch, type Ref} from "vue"
import {useIntervalFn} from "@vueuse/core"
import type {LogEntry} from "@kestra-io/kestra-sdk"
import {LoopIterationError, type LoopIterationFailure, type LoopRoot} from "../utils/loopIterations"
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
export const POST_FINISH_REFRESH_DELAYS_MS = [1_000, 3_000, 6_000, 10_000, 15_000]

export type MergedLogsFailure = LoopIterationFailure

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
    const batchHistory = ref<number[]>([])
    let batchOffset = 0
    let page = 1
    let pageStarts: {start: number; cursor?: string}[] = []
    let seq = 0
    let cache: ChainCache = createChainCache()
    let cacheRootId: string | undefined

    const hasMore = computed(() => cursorMode.value ? Boolean(nextCursor.value) : lines.value.length < total.value)
    const hasPreviousBatch = computed(() => batchHistory.value.length > 0)
    const hasNextBatch = computed(() => Boolean(targets.value) && targets.value!.end < targets.value!.total)

    const kinds = () => [options.root.value?.kind ?? "NORMAL", "LOOP"].filter((kind, index, all) => all.indexOf(kind) === index)

    const search = (executionIds: string[], pageNumber: number, cursor?: string) => searchMergedLogs({
        executionIds,
        kinds: kinds(),
        levelParams: options.levelParams.value,
        q: options.q?.value,
        page: pageNumber,
        cursor,
    })

    function collectTargets(root: LoopRoot, offset: number) {
        if (cacheRootId !== root.id) {
            cache = createChainCache()
            cacheRootId = root.id
        }
        const collectOptions = {reserve: logUrlReserve({levelParams: options.levelParams.value, kinds: kinds(), q: options.q?.value}), cache, offset}
        return options.scope.value.length
            ? collectScopedTargets(root, options.scope.value, undefined, collectOptions)
            : collectFailedTargets(root, undefined, collectOptions)
    }

    async function load(keepExtent = false, offset = batchOffset): Promise<boolean> {
        const root = options.root.value
        if (!root?.id) return false
        const current = ++seq
        loading.value = true
        try {
            const resolved = await collectTargets(root, offset)

            const pagesToLoad = keepExtent ? page : 1
            const starts: typeof pageStarts = [{start: 0}]
            let collected: LogEntry[] = []
            let cursor: string | undefined
            let result = await search(resolved.executionIds, 1)
            let pagesLoaded = 1
            collected = collected.concat(result.results)
            cursor = result.nextCursor
            while (pagesLoaded < pagesToLoad && (result.cursorMode ? Boolean(cursor) : collected.length < result.total)) {
                starts.push({start: collected.length, cursor})
                result = await search(resolved.executionIds, pagesLoaded + 1, cursor)
                collected = collected.concat(result.results)
                cursor = result.nextCursor
                pagesLoaded++
            }
            if (current !== seq) return false
            batchOffset = offset
            targets.value = resolved
            lines.value = collected
            total.value = result.total
            nextCursor.value = cursor
            cursorMode.value = result.cursorMode
            page = pagesLoaded
            pageStarts = starts
            failure.value = undefined
            return true
        } catch (error) {
            if (current === seq) failure.value = error instanceof LoopIterationError ? error.failure : "unknown"
            return false
        } finally {
            if (current === seq) {
                loading.value = false
                loaded.value = true
            }
        }
    }

    const hasSameTargets = (resolved: LoopLogTargets) => {
        const known = new Set(targets.value?.executionIds)
        return resolved.executionIds.length === known.size && resolved.executionIds.every((id) => known.has(id))
    }

    async function refreshTail(): Promise<boolean> {
        const root = options.root.value
        const tail = pageStarts[page - 1]
        if (!root?.id || !targets.value || !tail) return load(true)
        const current = ++seq
        loading.value = true
        try {
            const resolved = await collectTargets(root, batchOffset)
            if (current !== seq) return false
            if (!hasSameTargets(resolved)) return load(true)

            const result = await search(resolved.executionIds, page, tail.cursor)
            if (current !== seq) return false
            targets.value = resolved
            lines.value = lines.value.slice(0, tail.start).concat(result.results)
            total.value = result.total
            nextCursor.value = result.nextCursor
            failure.value = undefined
            return true
        } catch (error) {
            if (current === seq) failure.value = error instanceof LoopIterationError ? error.failure : "unknown"
            return false
        } finally {
            if (current === seq) loading.value = false
        }
    }

    async function loadMore() {
        if (!hasMore.value || loadingMore.value || loading.value || !targets.value) return
        const current = seq
        loadingMore.value = true
        try {
            const cursor = nextCursor.value
            const result = await search(targets.value.executionIds, page + 1, cursor)
            if (current !== seq) return
            page += 1
            pageStarts = [...pageStarts, {start: lines.value.length, cursor}]
            lines.value = lines.value.concat(result.results)
            total.value = result.total
            nextCursor.value = result.nextCursor
        } catch {
            failure.value = "unknown"
        } finally {
            loadingMore.value = false
        }
    }

    async function nextBatch() {
        if (!hasNextBatch.value || loading.value) return
        const from = batchOffset
        if (await load(false, targets.value!.end)) batchHistory.value = [...batchHistory.value, from]
    }

    async function previousBatch() {
        if (!hasPreviousBatch.value || loading.value) return
        if (await load(false, batchHistory.value[batchHistory.value.length - 1])) batchHistory.value = batchHistory.value.slice(0, -1)
    }

    const scopeKey = computed(() => options.scope.value.map(({taskId, number}) => `${taskId}:${number}`).join(","))
    const searchKey = computed(() => `${options.root.value?.id ?? ""}|${scopeKey.value}|${JSON.stringify(options.levelParams.value)}|${options.q?.value ?? ""}`)
    watch(searchKey, () => {
        batchOffset = 0
        batchHistory.value = []
        load()
    }, {immediate: true})

    const refreshTimer = useIntervalFn(() => {
        if (!loading.value && !loadingMore.value) refreshTail()
    }, AUTO_REFRESH_MS, {immediate: false})
    let settleTimers: ReturnType<typeof setTimeout>[] = []
    const cancelSettling = () => {
        settleTimers.forEach(clearTimeout)
        settleTimers = []
    }

    // Logs are indexed asynchronously: the last lines of a finished execution can land after its state flips.
    const settleAfterFinish = () => {
        refreshTail()
        let elapsed = 0
        POST_FINISH_REFRESH_DELAYS_MS.forEach((delay, index) => {
            elapsed += delay
            const isLast = index === POST_FINISH_REFRESH_DELAYS_MS.length - 1
            settleTimers.push(setTimeout(() => {
                if (loading.value || loadingMore.value) return
                if (isLast) load(true)
                else refreshTail()
            }, elapsed))
        })
    }

    watch(options.running, (running, wasRunning) => {
        cancelSettling()
        if (running) {
            refreshTimer.resume()
            return
        }
        refreshTimer.pause()
        if (wasRunning) settleAfterFinish()
    }, {immediate: true})
    onScopeDispose(() => {
        seq++
        cancelSettling()
        refreshTimer.pause()
    })

    return {targets, lines, total, loading, loadingMore, loaded, failure, hasMore, hasPreviousBatch, hasNextBatch, refresh: () => load(true), loadMore, nextBatch, previousBatch}
}
