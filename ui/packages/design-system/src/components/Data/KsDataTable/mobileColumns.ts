import type {ComputedRef, InjectionKey} from "vue"

export interface KsDataTableMobileColumns {
    leading?: string
    title: string
    titleAside?: string
    meta?: string[]
    metaAside?: string
    actions?: string
}

export const MOBILE_COLUMNS_KEY: InjectionKey<ComputedRef<KsDataTableMobileColumns | undefined>> = Symbol("ks-data-table-mobile-columns")

export function mobileColumnClass(columns: KsDataTableMobileColumns, key: string | undefined): string {
    if (key === undefined) return "ks-mobile-hidden"
    if (columns.leading === key) return "ks-mobile-leading"
    if (columns.title === key) return "ks-mobile-title"
    if (columns.titleAside === key) return "ks-mobile-title-aside"
    if (columns.actions === key) return "ks-mobile-actions"
    const metaIndex = columns.meta?.indexOf(key) ?? -1
    if (metaIndex === 0) return "ks-mobile-meta ks-mobile-meta-first"
    if (metaIndex > 0) return "ks-mobile-meta"
    if (columns.metaAside === key) return "ks-mobile-meta-aside"
    return "ks-mobile-hidden"
}

export function mobileColumnOrder(columns: KsDataTableMobileColumns, key: string | undefined): number | undefined {
    const metaIndex = key === undefined ? -1 : columns.meta?.indexOf(key) ?? -1
    return metaIndex >= 0 ? metaIndex : undefined
}
