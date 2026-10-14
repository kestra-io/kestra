import type {ComputedRef, InjectionKey} from "vue"

export interface KsDataTableMobileColumns {
    leading?: string
    title: string
    titleAside?: string
    meta?: string[]
    metaAside?: string
}

export const MOBILE_COLUMNS_KEY: InjectionKey<ComputedRef<KsDataTableMobileColumns | undefined>> = Symbol("ks-data-table-mobile-columns")

export function mobileColumnClass(columns: KsDataTableMobileColumns, prop: string | undefined): string {
    if (prop === undefined) return "ks-mobile-hidden"
    if (columns.leading === prop) return "ks-mobile-leading"
    if (columns.title === prop) return "ks-mobile-title"
    if (columns.titleAside === prop) return "ks-mobile-title-aside"
    const metaIndex = columns.meta?.indexOf(prop) ?? -1
    if (metaIndex >= 0) return `ks-mobile-meta ks-mobile-meta-${metaIndex}`
    if (columns.metaAside === prop) return "ks-mobile-meta-aside"
    return "ks-mobile-hidden"
}
