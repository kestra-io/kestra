import type {
    LocationQuery,
    LocationQueryRaw,
    LocationQueryValue,
    LocationQueryValueRaw
} from "vue-router";
import {AppliedFilter} from "./filterTypes";

const LEVEL_FILTER_PREFIX = "filters[level][";
const LEVEL_EQUALS_FILTER_KEY = "filters[level][EQUALS]";
const LEGACY_LEVEL_FILTER_KEY = "level";

const firstStringValue = (
    value:
        | LocationQueryValue
        | LocationQueryValueRaw
        | (LocationQueryValue | LocationQueryValueRaw)[]
        | undefined
) => {
    if (Array.isArray(value)) {
        return typeof value[0] === "string" ? value[0] : undefined;
    }

    return typeof value === "string" ? value : undefined;
};

export const readRouteLevelFilter = (query: LocationQuery | LocationQueryRaw) => {
    const value =
        firstStringValue(query[LEVEL_EQUALS_FILTER_KEY]) ??
        firstStringValue(query[LEGACY_LEVEL_FILTER_KEY]);

    return value && value.length > 0 ? value : undefined;
};

export const hasUnsupportedRouteLevelComparator = (query: LocationQuery | LocationQueryRaw) =>
    Object.keys(query).some(
        (key) =>
            key === LEGACY_LEVEL_FILTER_KEY ||
            (key.startsWith(LEVEL_FILTER_PREFIX) && key !== LEVEL_EQUALS_FILTER_KEY)
    );

export const readAppliedLevelFilter = (filters: AppliedFilter[]) => {
    const levelFilter = filters.find((filter) => filter.key === "level");
    if (!levelFilter) {
        return undefined;
    }

    if (Array.isArray(levelFilter.value)) {
        const value = levelFilter.value[0];
        return typeof value === "string" && value.length > 0 ? value : undefined;
    }

    return typeof levelFilter.value === "string" && levelFilter.value.length > 0
        ? levelFilter.value
        : undefined;
};

export const normalizeRouteLevelFilter = (
    query: Record<string, any>,
    level: string | undefined
) => {
    const normalized = {...query};

    Object.keys(normalized).forEach((key) => {
        if (key.startsWith(LEVEL_FILTER_PREFIX)) {
            delete normalized[key];
        }
    });

    if (level) {
        normalized[LEVEL_EQUALS_FILTER_KEY] = level;
    }

    delete normalized[LEGACY_LEVEL_FILTER_KEY];

    return normalized;
};
