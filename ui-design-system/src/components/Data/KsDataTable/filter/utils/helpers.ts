import {LocationQuery} from "vue-router";
import {AppliedFilter, Comparators} from "./filterTypes";

const decodeURIComponentSafely = (value: string | (string | null)[]): string | string[] =>
    Array.isArray(value)
        ? value.filter(v => v !== null).map(decodeURIComponent)
        : decodeURIComponent(value);

export function getComparator(comparatorKey: keyof typeof Comparators): Comparators {
    return Comparators[comparatorKey];
}

export function keyOfComparator(comparator: Comparators): keyof typeof Comparators {
    return Object.entries(Comparators).find(([_, value]) => value === comparator)![0] as keyof typeof Comparators;
}

export const decodeSearchParams = (query: LocationQuery) =>
    Object.entries(query)
        .filter(([key]) => key.startsWith("filters[") || key === "q")
        .map(([key, value]) => {
            if (!value) return null;

            const match = key.match(/filters\[(.*?)]\[(.*?)](?:\[(.*?)])?/);
            if (!match) return null;

            const [, field, operation, subKey] = match;

            if (subKey) {
                return {
                    field,
                    value: `${subKey}:${decodeURIComponentSafely(value)}`,
                    operation
                };
            }

            return {
                field,
                value: decodeURIComponentSafely(value),
                operation
            };
        })
        .filter(v => v !== null);

type Filter = Pick<AppliedFilter, "key" | "comparator" | "value">;

export const encodeFiltersToQuery = (filters: Filter[], keyOfComparator: (comparator: any) => string) =>
    filters.reduce((query, filter) => {
        const {key, comparator, value} = filter;
        const comparatorKey = keyOfComparator(comparator);

        switch (key) {
            case "timeRange":
                if (typeof value === "object" && "startDate" in value) {
                    query["filters[startDate][GREATER_THAN_OR_EQUAL_TO]"] = value.startDate.toISOString();
                    query["filters[endDate][LESS_THAN_OR_EQUAL_TO]"] = value.endDate.toISOString();
                } else {
                    query[`filters[${key}][${comparatorKey}]`] = value?.toString() ?? "";
                }
                return query;
            default: {
                if (Array.isArray(value) && value.some(v => typeof v === "string" && v.includes(":"))) {
                    value.forEach((item: string) => {
                        const [k, v] = item.split(":", 2);
                        if (k && v) query[`filters[${key}][${comparatorKey}][${k}]`] = v;
                    });
                } else {
                    query[`filters[${key}][${comparatorKey}]`] = Array.isArray(value)
                        ? value.join(",")
                        : value instanceof Date
                            ? value.toISOString()
                            : value?.toString() ?? "";
                }
                return query;
            }
        }
    }, {} as Record<string, string>);

export const isValidFilter = (filter: Filter): boolean => {
    const {value} = filter;

    if (value == null || value === "") return false;

    switch (true) {
        case Array.isArray(value):
            return value.length > 0;
        case typeof value === "object" && "startDate" in value:
            return !!(value.startDate && value.endDate);
        case value instanceof Date:
            return true;
        default:
            return true;
    }
};

export const getUniqueFilters = <T extends { key: string }>(filters: T[]): T[] =>
    filters.filter((filter, index, self) =>
        index === self.findLastIndex(f => f.key === filter.key)
    );

export const clearFilterQueryParams = (query: Record<string, any>): void => {
    for (const key of Object.keys(query)) {
        if (key.startsWith("filters[")) delete query[key];
    }
};

export const isSearchPath = (name: string) =>
    ["home", "flows/list", "executions/list", "logs/list", "admin/triggers"].includes(name);