import {onMounted, onUpdated, useAttrs, useTemplateRef} from "vue"

function isForwarded(key: string): boolean {
    return key.startsWith("data-") || (key.startsWith("aria-") && key !== "aria-label")
}

/**
 * Copies `data-*` and `aria-*` attrs onto the inputs inside the element with template ref `hostRef`: Element Plus
 * pickers bind `$attrs` to their tooltip, so `aria-invalid` would otherwise never reach the focused input.
 */
export function useInputAttrs(hostRef: string) {
    const host = useTemplateRef<HTMLElement>(hostRef)
    const attrs = useAttrs()
    let applied: string[] = []

    function pickerAttrs(): Record<string, unknown> {
        return Object.fromEntries(Object.entries(attrs).filter(([key]) => !isForwarded(key)))
    }

    function apply(): void {
        const forwarded = Object.entries(attrs).filter(([key, value]) => isForwarded(key) && value !== undefined && value !== null && value !== false)
        const keys = forwarded.map(([key]) => key)

        for (const input of host.value?.querySelectorAll("input") ?? []) {
            applied.filter((key) => !keys.includes(key)).forEach((key) => input.removeAttribute(key))
            forwarded.forEach(([key, value]) => input.setAttribute(key, String(value)))
        }
        applied = keys
    }

    onMounted(apply)
    onUpdated(apply)

    return {pickerAttrs}
}
