import type {Component} from "vue"
import type {ColumnConfig} from "../../../composables/useTableColumns"

export interface FlowsTableFlowRef {
    id: string;
    namespace: string;
}

export interface FlowsTableExtensionColumn extends ColumnConfig {
    cell: Component;
    header?: Component;
}

export interface FlowsTableExtension {
    columns: FlowsTableExtensionColumn[];
    load?: (flows: FlowsTableFlowRef[]) => void;
}

export function useFlowsTableExtension(): FlowsTableExtension {
    return {columns: []}
}
