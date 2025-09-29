import {Comparators} from "../../../composables/monaco/languages/filters/filterCompletion";

export type Shown = {
    shown: boolean;
};

export type Property = {
    shown: boolean;
    columns?: any[];
    displayColumns?: string[];
    storageKey?: string;
};

export type Buttons = {
    refresh: ({
        shown: true,
        callback: () => void;
    } | {
        shown: false
    }) & Shown;
    settings: Shown & {
        charts: Shown & {
            value: boolean;
            callback: (newVal: string) => void;
        };
    };
};

export type CurrentItem = {
    label?: string;
    field?: string;
    value: string[] | { startDate: Date; endDate: Date }[];
    operation?: string;
    comparator?: Comparators;
    persistent?: boolean;
};

type Pair = {
    label: string;
    value: string;
};

export type Option = {
    key: string;
    icon: any;
    label: string;
    value: {
        label: string;
        comparator?: Comparators;
        value: string[];
    };
    comparators: Comparators[];
};

export type Value = Pair;
