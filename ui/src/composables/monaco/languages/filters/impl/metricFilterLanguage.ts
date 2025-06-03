import {Comparators, Completion, FilterKeyCompletions} from "../filterCompletion.ts";
import {FilterLanguage} from "../filterLanguage.ts";

const metricFilterKeys: Record<string, FilterKeyCompletions> = {
    metric: new FilterKeyCompletions(
        [Comparators.EQUALS],
        async (store) => {
            const execution = store.getters["execution/execution"];

            const taskRuns: {id: string, taskId: string, value?: string}[] = execution.taskRunList ?? [];
            return taskRuns.map(taskRun => new Completion(
                taskRun.taskId + (taskRun.value ? ` - ${taskRun.value}` : ""),
                taskRun.id
            ));
        },
        true
    ),
}

class MetricFilterLanguage extends FilterLanguage {
    static readonly INSTANCE = new MetricFilterLanguage();

    private constructor() {
        super("metrics", metricFilterKeys);
    }
}

export default MetricFilterLanguage.INSTANCE as FilterLanguage;