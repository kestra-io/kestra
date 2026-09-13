import type {InjectionKey} from "vue"

export type SavedFilterAction = "save" | "apply" | "update" | "delete"

export interface SavedFilterAnalyticsEvent {
    action: SavedFilterAction;
    page: string;
    filtersCount: number;
}

export type SavedFilterAnalyticsTracker = (event: SavedFilterAnalyticsEvent) => void

export const SAVED_FILTER_ANALYTICS_INJECTION_KEY = Symbol("saved-filter-analytics") as InjectionKey<SavedFilterAnalyticsTracker>
