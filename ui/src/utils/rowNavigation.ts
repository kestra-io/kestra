const NON_NAVIGATING_TARGETS = "a, button, input, canvas, [role='button']"

export function isPlainRowActivation(column: {type?: string} | undefined, event: Event): boolean {
    if (column?.type === "selection") return false

    const click = event as MouseEvent
    // a modifier-click is the browser's own open-in-a-new-tab gesture, which RouterLink deliberately
    // lets through without preventDefault, so the current tab must stay where it is
    if (click.metaKey || click.ctrlKey || click.shiftKey || click.altKey || click.button !== 0) return false

    return !(event.target as HTMLElement | null)?.closest(NON_NAVIGATING_TARGETS)
}
