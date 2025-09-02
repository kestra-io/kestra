export interface NoCodeProps {
    creatingTask?: boolean;
    editingTask?: boolean;
    parentPath?: string;
    refPath?: number;
    position?: "before" | "after";
    blockSchemaPath?: string;
    fieldName?: string | undefined;
}