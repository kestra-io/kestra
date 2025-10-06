import {LocationQuery} from "vue-router";

function decodeURIComponentWithArray(value: string | (string|null)[]): string | string[] {
    if (Array.isArray(value)) {
        return value.filter(v => v !== null).map(v => decodeURIComponent(v));
    }
    return decodeURIComponent(value);
}

export const decodeSearchParams = (query: LocationQuery) => {
    const params = Object.entries(query)
        .filter(([key]) => (key.startsWith("filters[") || key === "q"))
        .map(([key, value]) => {
            if(value === null){
                return null;
            }

            const match = key.match(/filters\[(.*?)]\[(.*?)](?:\[(.*?)])?/);

            if (!match) return null;

            const [, field, operation, subKey] = match;

            if (field === "labels" && subKey) {
                return {
                    field: field,
                    value: `${subKey}:${decodeURIComponentWithArray(value)}`,
                    operation
                };
            }

            const label = field;
            const comparator = {value: operation};

            return {field: label, value: decodeURIComponentWithArray(value), operation: comparator.value};
        })
        .filter(val => val !== null);
    return params;
};
export const isSearchPath = (name: string) => ["home", "flows/list", "executions/list", "logs/list", "admin/triggers"].includes(name);