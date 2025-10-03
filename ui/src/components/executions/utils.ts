interface Label {
    key: string | null;
    value: string | null;
}

interface FilterResult {
    labels: Label[];
    error?: boolean;
}

export const filterValidLabels = (labels: Label[]): FilterResult => {
    const validLabels = labels.filter(label => label.key !== null && label.value !== null && label.key !== "" && label.value !== "");
    return validLabels.length === labels.length ? {labels} : {labels: validLabels, error: true};
};
