export type Dashboard = {
    id: string;
    title?: string;
    sourceCode?: string;
    [key: string]: unknown;
};

export type Chart = {
    id: string;
    [key: string]: unknown;
};
