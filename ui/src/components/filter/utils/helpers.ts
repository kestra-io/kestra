import {LocationQuery} from "vue-router";

const decodeURIComponentSafely = (value: string | (string | null)[]): string | string[] =>
    Array.isArray(value)
        ? value.filter(v => v !== null).map(decodeURIComponent)
        : decodeURIComponent(value);

export const decodeSearchParams = (query: LocationQuery) =>
    Object.entries(query)
        .filter(([key]) => key.startsWith("filters[") || key === "q")
        .map(([key, value]) => {
            if (!value) return null;

            const match = key.match(/filters\[(.*?)]\[(.*?)](?:\[(.*?)])?/);
            if (!match) return null;

            const [, field, operation, subKey] = match;

            if (field === "labels" && subKey) {
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
        .filter(Boolean);

interface Filter {
    key: string;
    comparator: any;
    value: any;
}

export const encodeFiltersToQuery = (filters: Filter[], keyOfComparator: (comparator: any) => string) =>
    filters.reduce((query, filter) => {
        let value = filter.value;

        if (filter.key === "timeRange" && typeof value === "object" && "startDate" in value) {
            const comparatorKey = keyOfComparator(filter.comparator);
            query[`filters[startDate][${comparatorKey}]`] = value.startDate.toISOString();
            query[`filters[endDate][${comparatorKey}]`] = value.endDate.toISOString();
            return query;
        }

        if (filter.key === "labels" && Array.isArray(value)) {
            value.forEach((label: string) => {
                const [key, val] = label.split(":", 2);
                if (key && val) {
                    const comparatorKey = keyOfComparator(filter.comparator);
                    query[`filters[labels][${comparatorKey}][${key}]`] = val;
                }
            });
            return query;
        }

        if (Array.isArray(value)) {
            value = value.join(",");
        } else if (typeof value === "object" && "startDate" in value) {
            value = `${value.startDate.toISOString()},${value.endDate.toISOString()}`;
        }

        const comparatorKey = keyOfComparator(filter.comparator);
        query[`filters[${filter.key}][${comparatorKey}]`] = value?.toString() || "";
        return query;
    }, {} as Record<string, string>);

export const isValidFilter = (filter: Filter): boolean => {
    const {value} = filter;
    if (Array.isArray(value)) return value.length > 0;
    if (typeof value === "object" && "startDate" in value) return !!(value.startDate && value.endDate);
    if (value instanceof Date) return true;
    return value !== "" && value !== null && value !== undefined;
};

export const getUniqueFilters = <T extends {key: string}>(filters: T[]): T[] =>
    filters.filter((filter, index, self) =>
        index === self.findLastIndex(f => f.key === filter.key)
    );

export const clearFilterQueryParams = (query: Record<string, any>): void =>
    Object.keys(query).forEach(key => {
        if (key.startsWith("filters[")) delete query[key];
    });

export const isSearchPath = (name: string) =>
    ["home", "flows/list", "executions/list", "logs/list", "admin/triggers"].includes(name);