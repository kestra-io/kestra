import {Comparators, Completion, FilterKeyCompletions, keyOfComparator, ValueCompletions} from "./filterCompletion";
import {useValues} from "../../../../components/filter/composables/useValues";
import {Store} from "vuex";

type FilterKeyCompletionEntries = [
    ({ key: string, regex: RegExp }),
    FilterKeyCompletions
][];

export abstract class FilterLanguage {
    private readonly _domain: string | undefined;
    private readonly _filterKeyCompletions: FilterKeyCompletionEntries;
    private static readonly NESTED_KEY_PLACEHOLDER = "NESTED_KEY";

    get domain(): string | undefined {
        return this._domain;
    }

    protected constructor(domain: string | undefined, filterKeyCompletions: Record<string, FilterKeyCompletions>) {
        this._domain = domain;
        this._filterKeyCompletions = [
            ...(Object.entries(filterKeyCompletions).map(([key, filterKeyCompletion]) => [
                {
                    key: key,
                    regex: new RegExp("^" + key.replaceAll(".", "\\.").replaceAll(/\$?\{([^}]*)}/g, ".*") + "$")
                },
                filterKeyCompletion
            ]) as FilterKeyCompletionEntries),
            [
                {
                    key: "text",
                    regex: /^text$/
                },
                new FilterKeyCompletions([Comparators.EQUALS, Comparators.NOT_EQUALS, Comparators.REGEX])
            ]
        ];
    }

    static withNestedKeyPlaceholder(keyLabel: string) {
        return keyLabel.replaceAll(/\$\{[^}]*}|(?<=\.)[\s\S]*/g, FilterLanguage.NESTED_KEY_PLACEHOLDER);
    }

    private completionForKey(inputKey: string): FilterKeyCompletions | undefined {
        const [_, completion] = this._filterKeyCompletions.find(([keyMatcher]) => keyMatcher.regex.test(inputKey)) ?? [];
        return completion;
    }

    keyMatchers(): RegExp[] {
        return this._filterKeyCompletions.map(([{regex}]) => regex);
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

    comparatorsPerKey(): Record<string, (keyof typeof Comparators)[]> {
        return this._filterKeyCompletions.reduce((acc, [{key}, filterKeyCompletion]) => {
            acc[FilterLanguage.withNestedKeyPlaceholder(key)] = filterKeyCompletion.comparators.map(comparator => keyOfComparator(comparator));
            return acc;
        }, {} as Record<string, (keyof typeof Comparators)[]>);
    }

    async comparatorCompletion(key: string): Promise<Completion[]> {
        const completion = this.completionForKey(key);
        if (completion === undefined) {
            return [];
        }

        return completion.comparators.map(comparator => new Completion(keyOfComparator(comparator), comparator));
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