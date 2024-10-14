interface Label {
    key: string | null;
    value: string | null;
}

interface FilterResult {
    labels: Label[];
    error?: boolean;
}

export const filterLabels = (labels: Label[]): FilterResult => {
    const invalid = labels.some(label => label.key === null || label.value === null || label.key === "" || label.value === "");
    return invalid ? {labels, error: true} : {labels};
};
