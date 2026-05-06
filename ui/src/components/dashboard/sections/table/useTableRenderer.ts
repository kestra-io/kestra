import type {Component} from "vue";
import {KsExecutionStatus} from "@kestra-io/design-system";

import Date from "./columns/Date.vue";
import Duration from "./columns/Duration.vue";
import Link from "./columns/Link.vue";
import Namespace from "./columns/Namespace.vue";

export interface TableRendererOptions {
    chartColumns: () => Record<string, any> | undefined;
    routeNamespace: () => string | undefined;
}

/**
 * Maps a column's `field` (from the chart YAML) to the cell component used to
 * render its value, plus the props that component expects.
 *
 * Why this lives here and not inline in each chart component: the Link cell
 * needs both `FLOW_ID` and `NAMESPACE` available in the row to build the
 * router-link target. Per-flow overviews don't have a NAMESPACE column in
 * their YAML (it's redundant — the namespace is in the route), so we augment
 * with the route's namespace when missing. Keeping that augmentation in one
 * place avoids duplicating the workaround in every chart.
 */
export function useTableRenderer(options: TableRendererOptions) {
    const augmentedColumns = (): Record<string, any> => {
        const columns = options.chartColumns() ?? {};
        const hasNamespace = Object.values(columns).some((c: any) => c?.field === "NAMESPACE");
        if (hasNamespace) return columns;
        return {...columns, namespace: {field: "NAMESPACE", displayName: "Namespace"}};
    };

    const augmentedRow = (row: Record<string, any>): Record<string, any> => {
        const fromRow = row.namespace as string | undefined;
        if (typeof fromRow === "string" && fromRow.length > 0) return row;
        const fromRoute = options.routeNamespace();
        return fromRoute !== undefined ? {...row, namespace: fromRoute} : row;
    };

    const resolvedComponent = (field: string): Component | undefined => {
        switch (field) {
        case "ID":
        case "FLOW_ID":
            return Link;
        case "NAMESPACE":
            return Namespace;
        case "STATE":
            return KsExecutionStatus;
        case "DURATION":
            return Duration;
        default:
            return field?.toLowerCase().includes("date") ? Date : undefined;
        }
    };

    const resolvedProps = (field: string, key: string, row: Record<string, any>): Record<string, any> => {
        const baseProps = {field: key, row: augmentedRow(row), columns: augmentedColumns()};

        switch (field) {
        case "ID":
            return {...baseProps, execution: true};
        case "FLOW_ID":
            return {...baseProps, flow: true};
        case "NAMESPACE":
            return {field: row[key]};
        case "STATE":
            return {size: "small", status: row[key]?.toString()};
        case "DURATION":
            return {field: row[key], startDate: row["start_date"] ?? row["startDate"]};
        default:
            if (field?.toLowerCase().includes("date")) {
                return {field: row[key]};
            }
            return {};
        }
    };

    return {resolvedComponent, resolvedProps};
}
