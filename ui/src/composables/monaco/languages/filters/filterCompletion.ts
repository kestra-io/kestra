import {useValues} from "../../../../components/filter/composables/useValues.ts";
import {Value} from "../../../../components/filter/utils/types.ts";
import {Store} from "vuex";

export enum Comparators {
    EQUALS = "=",
    NOT_EQUALS = "!=",
    GREATER_THAN = ">",
    LESS_THAN = "<",
    GREATER_THAN_OR_EQUAL_TO = ">=",
    LESS_THAN_OR_EQUAL_TO = "<=",
    STARTS_WITH = "^=",
    ENDS_WITH = "$=",
    CONTAINS = "*=",
    REGEX = "~="
}

export class Completion {
    private readonly _label: string;
    private readonly _value: string;

    get label(): string {
        return this._label;
    }

    get value(): string {
        return this._value;
    }

    constructor(label: string, value: string) {
        this._label = label;
        this._value = value;
    }
}

export const PICK_DATE_VALUE = "PICK_DATE";

export type ValueCompletions = Value[] | typeof PICK_DATE_VALUE;

export class FilterKeyCompletions {
    private readonly _comparators: Comparators[];
    private readonly _valuesFetcher: (store: Store<Record<string, any>>, hardcodedValues: ReturnType<typeof useValues>["VALUES"]) => Promise<ValueCompletions>;
    private readonly _allowMultipleValues: boolean;

    constructor(comparators: Comparators[], valuesFetcher: (store: Store<Record<string, any>>, hardcodedValues: ReturnType<typeof useValues>["VALUES"]) => Promise<ValueCompletions> = async () => [], allowMultipleValues?: boolean) {
        this._comparators = comparators;
        this._valuesFetcher = valuesFetcher;
        this._allowMultipleValues = allowMultipleValues ?? false;
    }

    get comparators(): Comparators[] {
        return this._comparators;
    }

    get valuesFetcher(): (store: Store<Record<string, any>>, hardcodedValues: ReturnType<typeof useValues>["VALUES"]) => Promise<ValueCompletions> {
        return this._valuesFetcher;
    }

    get allowMultipleValues(): boolean {
        return this._allowMultipleValues;
    }
}