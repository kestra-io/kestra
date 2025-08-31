import {Comparators, Completion, FilterKeyCompletions} from "../filterCompletion.ts";
import type {useExecutionsStore} from "../../../../../stores/executions";
import {FilterLanguage} from "../filterLanguage.ts";

const metricFilterKeys: (executionsStore: ReturnType<typeof useExecutionsStore>) => Record<string, FilterKeyCompletions> = (executionsStore) => ({
    metric: new FilterKeyCompletions(
        [Comparators.EQUALS],
        async () => {
            const taskRuns = executionsStore.execution?.taskRunList ?? [];
            return taskRuns.map(taskRun => new Completion(
                taskRun.taskId + (taskRun.value ? ` - ${taskRun.value}` : ""),
                taskRun.id
            ));
        },
        true
    ),
})

export class MetricFilterLanguage extends FilterLanguage {
    constructor(executionsStore: ReturnType<typeof useExecutionsStore>) {
        super("metrics", metricFilterKeys(executionsStore));
    }
}

export default MetricFilterLanguage;