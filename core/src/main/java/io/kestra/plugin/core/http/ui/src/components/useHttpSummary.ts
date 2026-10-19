import { onMounted, ref, watch, type Ref } from "vue";
import { parseHttpOutputs, type HttpSummary, type HttpTaskKind } from "./httpSummary";

export type FetchOutputs = (query?: { taskRunId?: string }) => Promise<Record<string, unknown>>;

/**
 * Loads a task run's outputs through the host fetcher and parses them. `refreshKey` changes when
 * the task run does (new execution, new state), which re-reads the outputs.
 */
export function useHttpSummary(
    fetchOutputs: () => FetchOutputs | undefined,
    kind: () => HttpTaskKind,
    refreshKey: () => string,
) {
    const summary: Ref<HttpSummary | undefined> = ref();
    const loading = ref(false);
    const failed = ref(false);
    let sequence = 0;

    async function load() {
        const fetcher = fetchOutputs();
        if (!fetcher) {
            summary.value = undefined;
            return;
        }
        const current = ++sequence;
        loading.value = true;
        failed.value = false;
        try {
            const outputs = await fetcher();
            if (current !== sequence) return;
            summary.value = parseHttpOutputs(outputs, kind());
        } catch (error) {
            if (current !== sequence) return;
            console.error("[HttpSummary] Failed to load task outputs:", error);
            summary.value = undefined;
            failed.value = true;
        } finally {
            if (current === sequence) loading.value = false;
        }
    }

    onMounted(load);
    watch(refreshKey, load);

    return { summary, loading, failed, reload: load };
}

interface TaskRunLike {
    taskId: string;
    state?: { current?: string };
}

export function taskRunRefreshKey(
    execution: { id?: string; taskRunList?: TaskRunLike[] } | undefined,
    taskId: string | undefined,
): string {
    const runs = execution?.taskRunList?.filter((run) => run.taskId === taskId) ?? [];
    return `${execution?.id ?? ""}:${runs.length}:${runs.at(-1)?.state?.current ?? ""}`;
}