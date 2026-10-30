import {RouterLink} from "vue-router"
import type {KsBreadcrumbItem} from "./types"

export type ResolvedItem = {tag: typeof RouterLink | "a" | "span"; attrs: Record<string, unknown>}

export function resolveItem(item: KsBreadcrumbItem): ResolvedItem {
    if (item.disabled) return {tag: "span", attrs: {}}
    if (item.link) return {tag: RouterLink, attrs: {to: item.link}}
    if (item.onClick) return {
        tag: "a",
        attrs: {href: "#", onClick: (e: Event) => { e.preventDefault(); item.onClick?.() }},
    }
    return {tag: "span", attrs: {}}
}
