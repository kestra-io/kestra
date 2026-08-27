import {h, ref, render, watch, type Component, type Ref, type VNode} from "vue"
import * as monaco from "monaco-editor/editor/editor.api"
import uniqBy from "lodash/uniqBy"
import {STATES} from "../utils/state"
import {OVERFLOW_WIDGETS_ID} from "../utils/monacoSetup"
import {DATE_PICKER_SUGGESTION_LABEL} from "./useEditorDatePicker"
import type {TaskIconProps} from "./taskIcon"

const KESTRA_ICON_WRAPPER_CLASS = "kestra-icon-wrapper"
const SASH_DRAG_DISTANCE = 80
const PLUGIN_FQCN = /^[a-z][\w$]*(?:\.[\w$]+)+$/

// Monaco suffixes the row aria-label with ", <kind>" (and ", docs: …" once resolved), so read the rendered label.
function suggestionLabel(row: HTMLElement): string | undefined {
    const rendered = row.querySelector(".monaco-icon-name-container")?.textContent?.trim()
    return rendered || row.getAttribute("aria-label")?.split(",")[0].trim()
}

type CodeEditor = monaco.editor.ICodeEditor

export interface SuggestWidgetIconsContext {
    taskIcon: Component
    loadTaskIcon: Ref<TaskIconProps["loadIcon"] | undefined>
    largeSuggestions: Ref<boolean | undefined>
    codeEditor: () => CodeEditor | undefined
    datePicker: {
        show: (editor: CodeEditor) => Promise<void>
        remove: (editor: CodeEditor) => void
    }
}

export function useSuggestWidgetIcons(ctx: SuggestWidgetIconsContext) {
    const suggestWidget = ref<HTMLElement>()
    const rowObserver = ref<MutationObserver>()
    const resizeObserver = ref<MutationObserver>()

    function replaceRowIcon(vsCodeIcon: HTMLElement, iconVNode: VNode) {
        vsCodeIcon.style.display = "none"
        const tempContainer = document.createElement("div")
        render(h("div", {class: `${KESTRA_ICON_WRAPPER_CLASS} d-flex align-items-center me-1`}, iconVNode), tempContainer)
        vsCodeIcon.after(tempContainer.firstElementChild!)
        tempContainer.remove()
    }

    function replaceRowsIcons(nodes: HTMLElement[]) {
        for (const node of uniqBy(nodes, n => n.id)) {
            const completionValue = suggestionLabel(node)
            if (!completionValue || node.getAttribute("data-index") === null) continue

            const vsCodeIcon = node.querySelector(".suggest-icon") as HTMLElement
            node.querySelector(`.${KESTRA_ICON_WRAPPER_CLASS}`)?.remove()

            if (PLUGIN_FQCN.test(completionValue) && ctx.loadTaskIcon.value) {
                replaceRowIcon(vsCodeIcon, h(ctx.taskIcon, {
                    cls: completionValue,
                    onlyIcon: true,
                    loadIcon: ctx.loadTaskIcon.value,
                }))
            } else if ((STATES as any)[completionValue] !== undefined) {
                replaceRowIcon(vsCodeIcon, h((STATES as any)[completionValue].icon))
            } else {
                vsCodeIcon.style.display = ""
            }
        }
    }

    function addedSuggestRows(mutations: MutationRecord[]): HTMLElement[] {
        return mutations.flatMap(({addedNodes}) => {
            const nodes = [...addedNodes]
            const maybeRows = nodes.filter((n) => (n as HTMLElement).classList?.contains("monaco-list-row"))
            for (const node of nodes) {
                let maybeRow: Element | null = null
                if (node instanceof Text) {
                    maybeRow = node.parentElement?.closest(".monaco-list-row") ?? null
                }
                if (maybeRow !== null) return [...maybeRows, maybeRow]
            }
            return maybeRows
        }) as HTMLElement[]
    }

    watch(suggestWidget, async (widget) => {
        if (widget === undefined) return
        const editor = ctx.codeEditor()

        if (widget.querySelector(".monaco-list-row") !== null) {
            replaceRowsIcons([...widget.getElementsByClassName("monaco-list-row")] as HTMLElement[])
        }

        rowObserver.value?.disconnect()
        rowObserver.value = new MutationObserver(mutations => {
            mutations.forEach(({removedNodes}) => {
                const datePickerRemoved = [...removedNodes.values()]
                    .some(n => n instanceof Text && n.textContent === DATE_PICKER_SUGGESTION_LABEL)
                if (datePickerRemoved && editor) ctx.datePicker.remove(editor)
            })

            const addedRows = addedSuggestRows(mutations)
            replaceRowsIcons(addedRows.filter(row => suggestionLabel(row) !== DATE_PICKER_SUGGESTION_LABEL))

            addedRows.forEach(async row => {
                if (!editor || suggestionLabel(row) !== DATE_PICKER_SUGGESTION_LABEL) return
                ;(editor.getContribution("editor.contrib.suggestController") as unknown as {
                    cancelSuggestWidget: () => void
                }).cancelSuggestWidget()
                await ctx.datePicker.show(editor)
            })
        })

        rowObserver.value.observe(widget, {childList: true, subtree: true})

        editor?.onDidChangeCursorPosition(() => ctx.datePicker.remove(editor))
    })

    function observeAndResize() {
        if (resizeObserver.value !== undefined) return

        resizeObserver.value = new MutationObserver(([{target, addedNodes}]) => {
            const simulateResizeOnSashAndDisconnect = (resizer: HTMLElement) => {
                if (!ctx.largeSuggestions.value) return

                resizeObserver.value?.disconnect()
                resizeObserver.value = undefined

                const origin = {x: resizer.getBoundingClientRect().left, y: resizer.getBoundingClientRect().top}
                const fire = (type: string, dx = 0) => resizer.dispatchEvent(
                    new MouseEvent(type, {bubbles: true, clientX: origin.x + dx, clientY: origin.y}),
                )
                fire("mouseenter"); fire("mouseover"); fire("mousedown")
                fire("mousemove", SASH_DRAG_DISTANCE); fire("mouseup", SASH_DRAG_DISTANCE)
                fire("mouseout", SASH_DRAG_DISTANCE); fire("mouseleave", SASH_DRAG_DISTANCE)
            }

            const targetHtmlElement = target as HTMLElement
            if (targetHtmlElement.classList.contains("monaco-sash")) {
                if (!targetHtmlElement.classList.contains("disabled")) {
                    simulateResizeOnSashAndDisconnect(targetHtmlElement)
                }
                return
            }

            const maybeSuggestWidget = addedNodes?.[0] as HTMLElement
            if (maybeSuggestWidget?.classList.contains("suggest-widget")) {
                suggestWidget.value = maybeSuggestWidget
                const resizer = maybeSuggestWidget.querySelector(".monaco-sash.vertical") as HTMLElement
                if (resizer.classList.contains("disabled")) {
                    resizeObserver.value!.disconnect()
                    resizeObserver.value?.observe(resizer, {attributeFilter: ["class"]})
                } else {
                    simulateResizeOnSashAndDisconnect(resizer)
                }
            }
        })

        const overflowNode = document.getElementById(OVERFLOW_WIDGETS_ID)
        const target = overflowNode?.querySelector(".overflowingContentWidgets") ?? overflowNode
        if (target) resizeObserver.value.observe(target, {childList: true})
    }

    function teardown() {
        resizeObserver.value?.disconnect()
        resizeObserver.value = undefined
        rowObserver.value?.disconnect()
        rowObserver.value = undefined
        suggestWidget.value = undefined
    }

    return {observeAndResize, teardown}
}
