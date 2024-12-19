export type Shown = {
    shown: boolean;
};

export type Buttons = {
    refresh: Shown & {
        callback: () => void;
    };
    settings: Shown & {
        charts: Shown & {
            value: boolean;
            callback: () => void;
        };
    };
};

export type CurrentItem = {
    label: string;
    value: string[] | { startDate: Date; endDate: Date }[];
    comparator?: Comparator;
    persistent?: boolean;
};

type Pair = {
    label: string;
    value: string;
};

export type Comparator = Pair & {
    multiple?: boolean;
};

export type Option = {
    key: string;
    icon: any;
    label: string;
    value: {
        label: string;
        comparator?: Comparator;
        value: string[];
    };
    comparators: Comparator[];
};

export type Value = Pair;
