import {Comparators, Completion, FilterKeyCompletions, PICK_DATE_VALUE} from "../filterCompletion.ts";
import {FilterLanguage} from "../filterLanguage.ts";

const flowMetricFilterKeys: Record<string, FilterKeyCompletions> = {
    task: new FilterKeyCompletions(
        [Comparators.EQUALS],
        async (store) => (store.state["flow"].tasksWithMetrics as string[]).map((value) => new Completion(
            value,
            value
        ))
    ),
    metric: new FilterKeyCompletions(
        [Comparators.EQUALS],
        async (store) => (store.state["flow"].metrics as string[]).map((value) => new Completion(
            value,
            value
        ))
    ),
    aggregation: new FilterKeyCompletions(
        [Comparators.EQUALS],
        async (_, hardcodedValues) => hardcodedValues.AGGREGATIONS
    ),
    startDate: new FilterKeyCompletions(
        [Comparators.GREATER_THAN_OR_EQUAL_TO, Comparators.GREATER_THAN, Comparators.LESS_THAN_OR_EQUAL_TO, Comparators.LESS_THAN, Comparators.EQUALS, Comparators.NOT_EQUALS],
        async () => PICK_DATE_VALUE
    ),
    endDate: new FilterKeyCompletions(
        [Comparators.LESS_THAN_OR_EQUAL_TO, Comparators.LESS_THAN, Comparators.GREATER_THAN_OR_EQUAL_TO, Comparators.GREATER_THAN, Comparators.EQUALS, Comparators.NOT_EQUALS],
        async () => PICK_DATE_VALUE
    ),
};

class FlowMetricFilterLanguage extends FilterLanguage {
    static readonly INSTANCE = new FlowMetricFilterLanguage();

    private constructor() {
        super("flow-metrics", flowMetricFilterKeys);
    }
}

export default FlowMetricFilterLanguage.INSTANCE as FilterLanguage;