import {Comparators} from "../../../../composables/monaco/languages/filters/filterCompletion";

export interface FilterKeyConfig {
    key: string;
    label: string;
    comparators: Comparators[];
    valueType: "text" | "select" | "date" | "multi-select";
    valueProvider?: () => Promise<FilterValue[]>;
    icon?: string;
    group?: string;
    searchable?: boolean;
    conflictsWith?: string[];
    showComparatorSelection?: boolean;
}

export interface FilterValue {
    label: string;
    value: string;
}

export interface AppliedFilter {
    id: string;
    key: string;
    keyLabel: string;
    comparator: Comparators;
    comparatorLabel: string;
    value: string | string[] | Date | {startDate: Date; endDate: Date};
    valueLabel: string;
    persistent?: boolean;
}

export interface SavedFilter {
    id: string;
    name: string;
    description?: string;
    filters: AppliedFilter[];
    createdAt: Date;
    global?: boolean;
}

export interface FilterConfiguration {
    title: string;
    searchPlaceholder?: string;
    keys: FilterKeyConfig[];
    defaultFilters?: AppliedFilter[];
}

export interface FilterProperties {
    shown: boolean;
    columns?: any[];
    displayColumns?: string[];
    storageKey?: string;
}

export interface TableOptions {
    chart?: { shown?: boolean; value?: boolean; callback?: (value: boolean) => void };
    columns?: { shown?: boolean };
    refresh?: { shown?: boolean; callback?: () => void };
}

export interface FilterButtons {
    savedFilters?: { shown?: boolean };
    tableOptions?: { shown?: boolean };
}

export const COMPARATOR_LABELS: Record<Comparators, string> = {
    [Comparators.EQUALS]: "equals",
    [Comparators.NOT_EQUALS]: "Not equals",
    [Comparators.IN]: "Is One Of",
    [Comparators.NOT_IN]: "Is Not One Of",
    [Comparators.GREATER_THAN]: "Greater Than",
    [Comparators.LESS_THAN]: "Less Than",
    [Comparators.GREATER_THAN_OR_EQUAL_TO]: "Greater Than or Equal",
    [Comparators.LESS_THAN_OR_EQUAL_TO]: "Less Than or Equal",
    [Comparators.STARTS_WITH]: "Starts With",
    [Comparators.ENDS_WITH]: "Ends With",
    [Comparators.CONTAINS]: "Contains",
    [Comparators.REGEX]: "Matches Pattern",
    [Comparators.PREFIX]: "Hierarchy",
};

export const COMPARATOR_DESCRIPTIONS: Record<Comparators, string> = {
    [Comparators.EQUALS]: "Exact match - value must be identical",
    [Comparators.NOT_EQUALS]: "Excludes exact matches - value must be different",
    [Comparators.IN]: "Matches any value from a list of options",
    [Comparators.NOT_IN]: "Excludes all values from a list of options",
    [Comparators.GREATER_THAN]: "Numeric/date comparison - value must be larger",
    [Comparators.LESS_THAN]: "Numeric/date comparison - value must be smaller",
    [Comparators.GREATER_THAN_OR_EQUAL_TO]: "Numeric/date comparison - value must be larger or equal",
    [Comparators.LESS_THAN_OR_EQUAL_TO]: "Numeric/date comparison - value must be smaller or equal",
    [Comparators.STARTS_WITH]: "Text begins with the specified characters",
    [Comparators.ENDS_WITH]: "Text ends with the specified characters",
    [Comparators.CONTAINS]: "Text includes the specified characters anywhere",
    [Comparators.REGEX]: "Advanced pattern matching using regular expressions",
    [Comparators.PREFIX]: "Namespace hierarchy matching (e.g., 'com.example' matches 'com.example.app')",
};