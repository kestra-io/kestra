export enum Comparators {
    EQUALS = "=",
    NOT_EQUALS = "!=",
    IN = "IN",
    NOT_IN = "NOT_IN",
    GREATER_THAN = ">",
    LESS_THAN = "<",
    GREATER_THAN_OR_EQUAL_TO = ">=",
    LESS_THAN_OR_EQUAL_TO = "<=",
    STARTS_WITH = "^=",
    ENDS_WITH = "$=",
    CONTAINS = "*=",
    REGEX = "~=",
    PREFIX = "^.=",
}

export const KV_COMPARATORS = [Comparators.EQUALS, Comparators.NOT_EQUALS];
export const TEXT_COMPARATORS = [
    Comparators.CONTAINS,
    Comparators.ENDS_WITH, 
    Comparators.STARTS_WITH, 
];

export interface FilterKeyConfig {
    key: string;
    label: string;
    description?: string;
    searchable?: boolean;
    comparators: Comparators[];
    showComparatorSelection?: boolean;
    valueProvider?: () => Promise<FilterValue[]>;
    valueType: "text" | "select" | "date" | "multi-select" | "key-value" | "radio";
    visibleByDefault?: boolean;
}

export interface FilterValue {
    label: string;
    value: string;
    description?: string;
}

export interface AppliedFilter {
    id: string;
    key: string;
    keyLabel: string;
    valueLabel: string;
    isDefaultVisible?: boolean;
    comparator: Comparators;
    comparatorLabel: string;
    value: string | string[] | Date | {startDate: Date; endDate: Date};
}

export interface SavedFilter {
    id: string;
    name: string;
    createdAt: Date;
    global?: boolean;
    description?: string;
    searchQuery?: string;
    filters: AppliedFilter[];
}

export interface FilterConfiguration {
    title: string;
    keys: FilterKeyConfig[];
    searchPlaceholder?: string;
    defaultFilters?: AppliedFilter[];
}

export interface TableProperties {
    shown: boolean;
    columns?: any[];
    storageKey?: string;
    displayColumns?: string[];
}

export interface TableOptions {
    chart?: { 
        shown?: boolean; 
        value?: boolean; 
        callback?: (value: boolean) => void 
    };
    columns?: {
        shown?: boolean
    };
    refresh?: { 
        shown?: boolean; 
        callback?: () => void 
    };
}

export const COMPARATOR_LABELS: Record<Comparators, string> = {
    [Comparators.EQUALS]: "Equals",
    [Comparators.NOT_EQUALS]: "Not Equals",
    [Comparators.IN]: "In",
    [Comparators.NOT_IN]: "Not In",
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
    [Comparators.EQUALS]: "filter.comparator_descriptions.EQUALS",
    [Comparators.NOT_EQUALS]: "filter.comparator_descriptions.NOT_EQUALS",
    [Comparators.IN]: "filter.comparator_descriptions.IN",
    [Comparators.NOT_IN]: "filter.comparator_descriptions.NOT_IN",
    [Comparators.GREATER_THAN]: "filter.comparator_descriptions.GREATER_THAN",
    [Comparators.LESS_THAN]: "filter.comparator_descriptions.LESS_THAN",
    [Comparators.GREATER_THAN_OR_EQUAL_TO]: "filter.comparator_descriptions.GREATER_THAN_OR_EQUAL_TO",
    [Comparators.LESS_THAN_OR_EQUAL_TO]: "filter.comparator_descriptions.LESS_THAN_OR_EQUAL_TO",
    [Comparators.STARTS_WITH]: "filter.comparator_descriptions.STARTS_WITH",
    [Comparators.ENDS_WITH]: "filter.comparator_descriptions.ENDS_WITH",
    [Comparators.CONTAINS]: "filter.comparator_descriptions.CONTAINS",
    [Comparators.REGEX]: "filter.comparator_descriptions.REGEX",
    [Comparators.PREFIX]: "filter.comparator_descriptions.PREFIX",
};