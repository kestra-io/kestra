import {Comparators, Completion, FilterKeyCompletions, ValueCompletions} from "./filterCompletion";
import {useValues} from "../../../../components/filter/composables/useValues";
import {Store} from "vuex";

type FilterKeyCompletionEntries = [
    ({ key: string, regex: RegExp }),
    FilterKeyCompletions
][];

export abstract class FilterLanguage {
    private readonly _domain: string | undefined;
    private readonly _filterKeyCompletions: FilterKeyCompletionEntries;

    get domain(): string | undefined {
        return this._domain;
    }

    protected constructor(domain: string | undefined, filterKeyCompletions: Record<string, FilterKeyCompletions>) {
        this._domain = domain;
        this._filterKeyCompletions = [
            ...(Object.entries(filterKeyCompletions).map(([key, filterKeyCompletion]) => [
                {
                    key: key,
                    regex: new RegExp(key.replaceAll(".", "\\.").replaceAll(/\$?\{([^}]*)}/g, ".*"))
                },
                filterKeyCompletion
            ]) as FilterKeyCompletionEntries),
            [
                {
                    key: "text",
                    regex: new RegExp("text")
                },
                new FilterKeyCompletions([Comparators.EQUALS, Comparators.NOT_EQUALS, Comparators.REGEX])
            ]
        ];
    }

    private completionForKey(inputKey: string): FilterKeyCompletions | undefined {
        const [_, completion] = this._filterKeyCompletions.find(([keyMatcher]) => keyMatcher.regex.test(inputKey)) ?? [];
        return completion;
    }

    async keyCompletion(): Promise<Completion[]> {
        return this._filterKeyCompletions
            .map(([{key}, {comparators}]) => {
                return new Completion(
                    key.replaceAll(/\$(\{[^}]*})/g, "$1"),
                    key.replaceAll(/\$?\{([^}]*)}/g, "") + (key.includes("{") ? "" : comparators[0])
                );
            });
    }

    async comparatorCompletion(key: string): Promise<Completion[]> {
        const completion = this.completionForKey(key);
        if (completion === undefined) {
            return [];
        }

        const comparatorValueByLabel = Object.entries(Comparators);
        return Object.entries(completion.comparators)
            .map(([_, value]) => new Completion(
                comparatorValueByLabel.find(([_, compValue]) => compValue === value)![0],
                value
            ));
    }

    async valueCompletion(store: Store<Record<string, any>>, hardcodedValues: ReturnType<typeof useValues>["VALUES"], key: string): Promise<ValueCompletions> {
        const completion = this.completionForKey(key);
        if (completion === undefined) {
            return [];
        }

        return completion.valuesFetcher(store, hardcodedValues);
    }

    multipleValuesAllowed(key: string): boolean {
        return this.completionForKey(key)?.allowMultipleValues ?? false;
    }
}