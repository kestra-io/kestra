import {ref, computed} from "vue"

export interface Crumb {
    path: string;
    label: string;
}

export interface NavFrame extends Crumb {
    schema: any;
}

const SCROLL_STABLE_FRAMES = 3
const SCROLL_MAX_FRAMES = 60

export function scrollThenFocus(el: HTMLElement, focusTarget?: HTMLElement | null) {
    const target = focusTarget ?? el.querySelector<HTMLElement>("input, textarea, select, button, [tabindex]") ?? el

    let lastTop = el.getBoundingClientRect().top
    let stableFrames = 0
    let frame = 0

    function check() {
        if (!el.isConnected) return
        frame += 1
        const top = el.getBoundingClientRect().top
        stableFrames = Math.abs(top - lastTop) < 0.5 ? stableFrames + 1 : 0
        lastTop = top

        if (stableFrames >= SCROLL_STABLE_FRAMES || frame >= SCROLL_MAX_FRAMES) {
            target.focus({preventScroll: true})
            return
        }
        requestAnimationFrame(check)
    }

    el.scrollIntoView({behavior: "smooth", block: "center"})
    requestAnimationFrame(check)
}

/**
 * Opens every collapsed TaskObject group (`.group:not(.is-open)`) between el and the form root, so
 * a jump target hidden behind a collapsed section is actually visible before scrollThenFocus runs.
 */
export function openCollapsedGroups(el: HTMLElement) {
    let current: HTMLElement | null = el
    while (current) {
        if (current.classList.contains("group") && !current.classList.contains("is-open")) {
            current.querySelector<HTMLElement>(":scope > .group-head")?.click()
        }
        current = current.parentElement
    }
}

export function useFieldNavigation() {
    const stack = ref<NavFrame[]>([])

    const current = computed<NavFrame | undefined>(() => stack.value[stack.value.length - 1])

    function push(frame: NavFrame) {
        stack.value = [...stack.value, frame]
    }

    function pop() {
        stack.value = stack.value.slice(0, -1)
    }

    function popTo(index: number) {
        stack.value = stack.value.slice(0, index + 1)
    }

    function reset() {
        if (stack.value.length) stack.value = []
    }

    return {stack, current, push, pop, popTo, reset}
}

export type FieldNavigation = ReturnType<typeof useFieldNavigation>;
