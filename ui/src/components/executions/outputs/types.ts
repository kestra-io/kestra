export interface ExplorerItem {
    label: string;
    value: unknown;
    type: string;
    preview: string;
    expression: string;
    taskRunId?: string;
}

export interface ExplorerSection {
    key: string;
    label: string;
    items: ExplorerItem[];
}
