import {computed} from "vue"
import {useRoute} from "vue-router"
import {useStorage} from "@vueuse/core"
import type {AppliedFilter, FilterGroup, LogicalOperator, SavedFilter} from "../utils/filterTypes"
import {isWrapperGroup} from "../utils/filterTypes"

const isDateString = (value: any) =>
    typeof value === "string" && !isNaN(Date.parse(value)) && value.includes("T")

const deserializeAppliedFilter = (f: any): AppliedFilter => ({
    ...f,
    value: f.value?.startDate && f.value?.endDate
        ? {startDate: new Date(f.value.startDate), endDate: new Date(f.value.endDate)}
        : isDateString(f.value)
            ? new Date(f.value)
            : f.value,
})

// Saved filters store groups alongside the flat `filters` list — dates inside
// grouped conditions need the same deserialization as the flat ones.
const deserializeGroup = (group: FilterGroup): FilterGroup =>
    isWrapperGroup(group)
        ? {
            ...group,
            children: group.children.map((child) => ({
                ...child,
                filters: child.filters.map(deserializeAppliedFilter),
            })),
        }
        : {...group, filters: group.filters.map(deserializeAppliedFilter)}

const deserializeDates = (filter: SavedFilter): SavedFilter => ({
    ...filter,
    filters: filter.filters.map(deserializeAppliedFilter),
    groups: filter.groups?.map(deserializeGroup),
    createdAt: new Date(filter.createdAt),
})

export function useSavedFilters(prefix: string) {
    const route = useRoute()

    const storageKey = computed(() => {
        const routeKey = String(route.name || route.path.replace(/\//g, "_").replace(/^_/, ""))
        return `saved_filters_${prefix}_${routeKey}`
    })

    const savedFilters = useStorage<SavedFilter[]>(storageKey.value, [], localStorage, {
        serializer: {
            read: (v: string) => JSON.parse(v).map(deserializeDates),
            write: (v: SavedFilter[]) => JSON.stringify(v),
        },
    })

    const saveFilter = (
        name: string,
        description: string,
        filters: AppliedFilter[],
        groups?: FilterGroup[],
        topLogical?: LogicalOperator,
    ) => {
        savedFilters.value = [...savedFilters.value, {
            id: `saved_${Date.now()}`,
            name,
            description,
            filters: [...filters],
            groups: groups ? [...groups] : undefined,
            topLogical,
            createdAt: new Date(),
        }]
    }

    const updateSavedFilter = (
        id: string,
        name: string,
        description: string,
        filters: AppliedFilter[],
        groups?: FilterGroup[],
        topLogical?: LogicalOperator,
    ) => {
        const index = savedFilters.value.findIndex((f) => f.id === id)
        if (index !== -1) {
            savedFilters.value[index] = {
                ...savedFilters.value[index],
                name,
                description,
                filters: [...filters],
                groups: groups ? [...groups] : undefined,
                topLogical,
            }
        }
    }

    const deleteSavedFilter = (savedFilter: SavedFilter) => {
        savedFilters.value = savedFilters.value.filter((f) => f.id !== savedFilter.id)
    }

    return {
        savedFilters: computed(() => savedFilters.value),
        saveFilter,
        updateSavedFilter,
        deleteSavedFilter,
    }
}
