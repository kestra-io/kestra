import {nextTick, ref, type Ref} from "vue"
import {ALL_SECTIONS, findNestedPath} from "./blockSections"
import type {BlockSection} from "../../../utils/flowableBlockOps"

export function useCanvasFocus(
    editorEl: Ref<HTMLElement | undefined>,
    sectionList: (section: BlockSection) => Record<string, unknown>[],
) {
    const focusedId = ref<string>()

    function navigableCards(): HTMLElement[] {
        if (!editorEl.value) return []
        return [...editorEl.value.querySelectorAll<HTMLElement>("[data-block-id]")].filter(el => el.offsetParent !== null)
    }

    function focusedCard(): HTMLElement | undefined {
        return navigableCards().find(el => el.getAttribute("data-block-id") === focusedId.value)
    }

    function cardFocusTarget(card: HTMLElement): HTMLElement {
        if (card.hasAttribute("tabindex") || card.tagName === "BUTTON") return card
        return card.querySelector<HTMLElement>("[data-test='flowable-cluster-header']") ?? card
    }

    function focusCanvasCard(id: string | undefined) {
        focusedId.value = id
        if (!id) return
        nextTick(() => {
            const card = focusedCard()
            if (!card) return
            cardFocusTarget(card).focus({preventScroll: true})
            card.scrollIntoView({block: "nearest"})
        })
    }

    function onCanvasFocusIn(event: FocusEvent) {
        const target = event.target as HTMLElement | null
        if (!target) return
        const id = target.closest("[data-block-id]")?.getAttribute("data-block-id")
        if (id) focusedId.value = id
    }

    function onCanvasEntryFocus() {
        const first = navigableCards()[0]
        if (first) focusCanvasCard(first.getAttribute("data-block-id") ?? undefined)
    }

    function moveFocus(direction: 1 | -1) {
        const cards = navigableCards()
        if (!cards.length) return
        const ids = cards.map(el => el.getAttribute("data-block-id") ?? "")
        const current = focusedId.value ? ids.indexOf(focusedId.value) : -1
        const next = current < 0 ? (direction > 0 ? 0 : cards.length - 1) : (current + direction + cards.length) % cards.length
        focusCanvasCard(ids[next] || undefined)
    }

    function focusedClusterHeader(): HTMLElement | undefined {
        const card = focusedCard()
        if (!card) return undefined
        return card.matches("[data-test='flowable-cluster-header']")
            ? card
            : (card.querySelector<HTMLElement>("[data-test='flowable-cluster-header']") ?? undefined)
    }

    function stepInto() {
        const header = focusedClusterHeader()
        if (!header) return
        if (header.getAttribute("aria-expanded") === "false") {
            header.click()
            return
        }
        nextTick(() => {
            const card = focusedCard()
            const cards = navigableCards()
            const current = focusedId.value ? cards.findIndex(el => el.getAttribute("data-block-id") === focusedId.value) : -1
            const next = cards[current + 1]
            if (card && next && current >= 0 && card.contains(next)) {
                focusCanvasCard(next.getAttribute("data-block-id") ?? focusedId.value)
            }
        })
    }

    function stepOut() {
        const header = focusedClusterHeader()
        if (header?.getAttribute("aria-expanded") === "true") {
            header.click()
            return
        }
        const card = focusedCard()
        const parent = card?.parentElement?.closest<HTMLElement>("[data-block-id]")
        if (parent) {
            focusCanvasCard(parent.getAttribute("data-block-id") ?? focusedId.value)
        }
    }

    function openFocused() {
        const card = focusedCard()
        if (!card) return
        const clusterHeader = card.querySelector<HTMLElement>("[data-test='flowable-cluster-header']")
        if (clusterHeader) {
            clusterHeader.click()
        } else {
            card.click()
        }
    }

    function actionInFocused(selector: string) {
        focusedCard()?.querySelector<HTMLElement>(selector)?.click()
    }

    function focusedBlockPath(): string | undefined {
        const id = focusedId.value
        if (!id) return undefined
        for (const section of ALL_SECTIONS) {
            const found = findNestedPath(sectionList(section), id, section)
            if (found) return found
        }
        return undefined
    }

    function focusedBlockDisplayName(): string {
        const card = focusedCard()
        return card?.querySelector<HTMLElement>("[data-test='block-card-id']")?.textContent?.trim() || focusedId.value || ""
    }

    function focusedBlockIsFlowable(): boolean {
        return Boolean(focusedCard()?.querySelector("[data-test='flowable-cluster-header']"))
    }

    return {
        focusedId,
        navigableCards,
        focusedCard,
        focusCanvasCard,
        onCanvasFocusIn,
        onCanvasEntryFocus,
        moveFocus,
        stepInto,
        stepOut,
        openFocused,
        actionInFocused,
        focusedBlockPath,
        focusedBlockDisplayName,
        focusedBlockIsFlowable,
    }
}

export type CanvasFocusApi = ReturnType<typeof useCanvasFocus>
