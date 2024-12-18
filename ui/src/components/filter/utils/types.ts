export type CurrentItem = {
    label: string;
    value: string[];
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
