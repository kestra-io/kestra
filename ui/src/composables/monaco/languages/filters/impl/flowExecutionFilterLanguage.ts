import {Comparators, FilterKeyCompletions, PICK_DATE_VALUE} from "../filterCompletion.ts";
import {FilterLanguage} from "../filterLanguage.ts";

const flowExecutionFilterKeys: Record<string, FilterKeyCompletions> = {
    state: new FilterKeyCompletions(
        [Comparators.EQUALS, Comparators.NOT_EQUALS],
        async (_, hardcodedValues) => hardcodedValues.EXECUTION_STATES,
        true
    ),
    scope: new FilterKeyCompletions(
        [Comparators.EQUALS, Comparators.NOT_EQUALS],
        async (_, hardcodedValues) => hardcodedValues.SCOPES
    ),
    childFilter: new FilterKeyCompletions(
        [Comparators.EQUALS, Comparators.NOT_EQUALS],
        async (_, hardcodedValues) => hardcodedValues.CHILDS
    ),
    timeRange: new FilterKeyCompletions(
        [Comparators.EQUALS],
        async (_, hardcodedValues) => hardcodedValues.RELATIVE_DATE
    ),
    startDate: new FilterKeyCompletions(
        [Comparators.GREATER_THAN_OR_EQUAL_TO, Comparators.GREATER_THAN, Comparators.LESS_THAN_OR_EQUAL_TO, Comparators.LESS_THAN, Comparators.EQUALS, Comparators.NOT_EQUALS],
        async () => PICK_DATE_VALUE
    ),
    endDate: new FilterKeyCompletions(
        [Comparators.LESS_THAN_OR_EQUAL_TO, Comparators.LESS_THAN, Comparators.GREATER_THAN_OR_EQUAL_TO, Comparators.GREATER_THAN, Comparators.EQUALS, Comparators.NOT_EQUALS],
        async () => PICK_DATE_VALUE
    ),
    "labels.{key}": new FilterKeyCompletions(
        [Comparators.EQUALS, Comparators.NOT_EQUALS],
        undefined,
        true
    ),
    triggerExecutionId: new FilterKeyCompletions(
        [Comparators.EQUALS, Comparators.NOT_EQUALS, Comparators.CONTAINS, Comparators.STARTS_WITH, Comparators.ENDS_WITH],
        undefined,
        true
    )
}

class FlowExecutionFilterLanguage extends FilterLanguage {
    static readonly INSTANCE = new FlowExecutionFilterLanguage();

    private constructor() {
        super("flow-executions", flowExecutionFilterKeys);
    }
}

export default FlowExecutionFilterLanguage.INSTANCE as FilterLanguage;