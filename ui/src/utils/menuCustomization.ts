import type {MenuItem} from "override/components/useLeftMenu"

export function menuSectionId(section: MenuItem): string {
    return section.id ?? section.title.toLowerCase().replaceAll(" ", "-")
}

export function flattenMenuItems(menu: MenuItem[]): MenuItem[] {
    return menu.flatMap((section) => section.child ?? [])
}

export function resolveSectionItemIds(
    menu: MenuItem[],
    menuItemOrder: Record<string, string[]>,
    sectionId: string,
): string[] {
    const savedOrder = menuItemOrder[sectionId]

    if (savedOrder !== undefined) {
        const existing = new Set(flattenMenuItems(menu).map((item) => item.id ?? ""))
        return savedOrder.filter((id) => existing.has(id))
    }

    const movedElsewhere = new Set<string>()
    for (const [otherSectionId, otherOrder] of Object.entries(menuItemOrder)) {
        if (otherSectionId === sectionId || otherOrder === undefined) continue
        for (const id of otherOrder) movedElsewhere.add(id)
    }

    const defaultSection = menu.find((section) => menuSectionId(section) === sectionId)
    return (defaultSection?.child ?? [])
        .filter((item) => !item.hidden && item.id && !movedElsewhere.has(item.id))
        .map((item) => item.id!)
}

export function pickItemsByIds(menu: MenuItem[], ids: string[]): MenuItem[] {
    const byId = new Map(flattenMenuItems(menu).map((item) => [item.id, item]))
    return ids
        .map((id) => byId.get(id))
        .filter((item): item is MenuItem => !!item && !item.hidden)
}

export function isMenuItemVisible(
    menuItemVisibility: Record<string, boolean>,
    item: MenuItem,
): boolean {
    if (!item.id) return true
    return menuItemVisibility[item.id] !== false
}
