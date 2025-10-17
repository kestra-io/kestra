export interface EditorTabProps {
    name: string;
    extension?: string;
    persistent?: boolean;
    path?: string;
    flow?: boolean;
    content?: string;
    dirty?: boolean;
    namespaceFiles?: boolean;
}
