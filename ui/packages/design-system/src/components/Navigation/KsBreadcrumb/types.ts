import type {Component} from "vue"
import type {RouterLink} from "vue-router"

type RouterLinkTo = InstanceType<typeof RouterLink>["$props"]["to"]

/** Fetches the entries of a breadcrumb menu or of a fly-out; a bar menu whose result is empty is not offered. */
export type KsBreadcrumbLoader = () => Promise<KsBreadcrumbItem[]>

export interface KsBreadcrumbItem {
    label: string
    link?: RouterLinkTo
    disabled?: boolean
    onClick?: () => void
    /** Shown before the label when the item is listed inside a menu. */
    icon?: Component
    /** Native tooltip of the item inside a menu, e.g. the full path behind a short label. */
    tooltip?: string
    /** The entry the page is on, highlighted inside a menu. */
    current?: boolean
    /** The name the item's content is listed under in a menu heading, when it is not the label itself. */
    scope?: string
    /** The entries at this item's level, offered by a chevron next to it in the bar and headed by the previous item. */
    siblings?: KsBreadcrumbLoader
    /** What lies under this item: offered by the bar's first item instead of siblings, and flown out from the item inside a menu. */
    children?: KsBreadcrumbLoader
}
