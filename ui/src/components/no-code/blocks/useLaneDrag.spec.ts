import {describe, it, expect, vi} from "vitest"
import {defineComponent, ref, nextTick} from "vue"
import {mount} from "@vue/test-utils"

import {useLaneDrag} from "./useLaneDrag"
import {BLOCK_DRAG_INJECTION_KEY} from "../injectionKeys"

import type {BlockDragContext} from "./useBlockDragAndDrop"

function createDragContext(
    overrides: Partial<BlockDragContext>,
): BlockDragContext {
    return {
        draggedPath: ref<string | null>(null),
        beginDrag: () => {},
        endDrag: () => {},
        canDropIn: () => false,
        dropAt: () => {},
        ...overrides,
    }
}

function createDragEvent({
    currentTarget = null,
    relatedTarget = null,
}: {
    currentTarget?: Node | null;
    relatedTarget?: Node | null;
} = {}): DragEvent {
    return {
        preventDefault: vi.fn(),
        stopPropagation: vi.fn(),
        currentTarget,
        relatedTarget,
    } as unknown as DragEvent
}

function mountLaneDrag(context?: BlockDragContext) {
    let result!: ReturnType<typeof useLaneDrag>

    const wrapper = mount(
        defineComponent({
            setup() {
                result = useLaneDrag(
                    () => "blocks",
                    () => 2,
                )

                return () => null
            },
        }),
        {
            global: {
                provide: context
                    ? {
                          [BLOCK_DRAG_INJECTION_KEY as unknown as string]:
                              context,
                      }
                    : {},
            },
        },
    )

    return {wrapper, result}
}

describe("useLaneDrag", () => {
    it("sets dropState based on whether the lane accepts the drop", () => {
        const draggedPath = ref<string | null>(null)
        const canDropIn = vi.fn((_parentPath: string) => true)

        const {wrapper, result} = mountLaneDrag(
            createDragContext({draggedPath, canDropIn}),
        )

        // Nothing is being dragged, so the state should be idle.
        expect(result.dropState.value).toBe("idle")

        // Start dragging an item.
        draggedPath.value = "blocks[0]"

        // The lane accepts the drop.
        expect(result.dropState.value).toBe("allowed")
        expect(canDropIn).toHaveBeenCalledWith("blocks")

        // The lane refuses the drop.
        canDropIn.mockReturnValue(false)
        draggedPath.value = "blocks[1]"

        expect(result.dropState.value).toBe("forbidden")

        wrapper.unmount()
    })

    it("sets dropState as idle when no dragContext is injected", () => {
        const {wrapper, result} = mountLaneDrag()

        expect(result.dropState.value).toBe("idle")

        wrapper.unmount()
    })

    it("begins a drag at lane path with the item index", () => {
        const beginDrag = vi.fn((_parentPath: string) => {})

        const {wrapper, result} = mountLaneDrag(
            createDragContext({beginDrag}),
        )

        const event = createDragEvent()

        result.onItemDragStart(event, 1)

        expect(beginDrag).toHaveBeenCalledWith("blocks[1]")

        wrapper.unmount()
    })

    it("does nothing when lane refuses drag over and drop", () => {
        const canDropIn = vi.fn((_parentPath: string) => false)
        const dropAt = vi.fn((_parentPath: string, _index: number) => {})

        const {wrapper, result} = mountLaneDrag(
            createDragContext({canDropIn, dropAt}),
        )

        const event = createDragEvent()

        result.onItemDragOver(event, 1)
        result.onItemDrop(event, 1)

        expect(canDropIn).toHaveBeenCalledWith("blocks")
        expect(canDropIn).toHaveBeenCalledTimes(2)

        expect(event.preventDefault).not.toHaveBeenCalled()
        expect(event.stopPropagation).not.toHaveBeenCalled()

        expect(dropAt).not.toHaveBeenCalled()

        wrapper.unmount()
    })

    it("calls dropAt with lane path and index when dropped on an accepting lane", () => {
        const canDropIn = vi.fn((_parentPath: string) => true)
        const dropAt = vi.fn((_parentPath: string, _index: number) => {})

        const {wrapper, result} = mountLaneDrag(
            createDragContext({canDropIn, dropAt}),
        )

        const event = createDragEvent()

        result.onItemDragOver(createDragEvent(), 1)
        expect(result.dragOverIndex.value).toBe(1)

        result.onItemDrop(event, 1)

        expect(canDropIn).toHaveBeenCalledWith("blocks")
        expect(event.preventDefault).toHaveBeenCalledOnce()
        expect(event.stopPropagation).toHaveBeenCalledOnce()
        expect(dropAt).toHaveBeenCalledWith("blocks", 1)
        expect(result.dragOverIndex.value).toBeNull()

        wrapper.unmount()
    })

    it("uses item count as index when performing trailing drop", () => {
        const canDropIn = vi.fn((_parentPath: string) => true)
        const dropAt = vi.fn((_parentPath: string, _index: number) => {})
        const endDrag = vi.fn()

        const event = createDragEvent()

        const {wrapper, result} = mountLaneDrag(
            createDragContext({canDropIn, dropAt, endDrag}),
        )

        result.onItemDragOver(createDragEvent(), 1)
        expect(result.dragOverIndex.value).toBe(1)

        result.onTrailingDrop(event)

        expect(canDropIn).toHaveBeenCalledWith("blocks")
        expect(event.preventDefault).toHaveBeenCalledOnce()
        expect(event.stopPropagation).toHaveBeenCalledOnce()
        expect(dropAt).toHaveBeenCalledWith("blocks", 2)
        expect(endDrag).toHaveBeenCalledOnce()
        expect(result.dragOverIndex.value).toBeNull()

        wrapper.unmount()
    })

    it("clears local drag state and ends the drag in context", () => {
        const canDropIn = vi.fn(() => true)
        const endDrag = vi.fn()
        const {wrapper, result} = mountLaneDrag(
            createDragContext({canDropIn, endDrag}),
        )
        const event = createDragEvent()

        result.onItemDragOver(event, 1)
        expect(result.dragOverIndex.value).toBe(1)

        result.onDragEnd()

        expect(result.dragOverIndex.value).toBeNull()
        expect(endDrag).toHaveBeenCalledOnce()

        wrapper.unmount()
    })

    it("clears the insertion cue when the dragged path is cleared", async () => {
        const draggedPath = ref<string | null>("blocks[0]")
        const canDropIn = vi.fn(() => true)
        const {wrapper, result} = mountLaneDrag(
            createDragContext({draggedPath, canDropIn}),
        )
        const event = createDragEvent()

        result.onItemDragOver(event, 1)
        expect(result.dragOverIndex.value).toBe(1)

        draggedPath.value = null
        await nextTick()

        expect(result.dragOverIndex.value).toBeNull()
        wrapper.unmount()
    })

    it("does not clears cue when relatedTarget is inside the container", () => {
        const canDropIn = vi.fn((_parentPath: string) => true)
        const {wrapper, result} = mountLaneDrag(
            createDragContext({canDropIn}),
        )

        const container = document.createElement("div")
        const relatedTarget = document.createElement("div")
        container.appendChild(relatedTarget)

        const event = createDragEvent({
            currentTarget: container,
            relatedTarget: relatedTarget,
        })

        result.onItemDragOver(event, 1)
        expect(result.dragOverIndex.value).toBe(1)

        result.onLaneDragLeave(event)

        expect(event.currentTarget).toBe(container)
        expect(event.relatedTarget).toBe(relatedTarget)
        expect(result.dragOverIndex.value).toBe(1)

        wrapper.unmount()
    })

    it("clears cue when relatedTarget is outside the container", () => {
        const canDropIn = vi.fn((_parentPath: string) => true)
        const {wrapper, result} = mountLaneDrag(
            createDragContext({canDropIn}),
        )

        const container = document.createElement("div")
        const relatedTarget = document.createElement("div")
        const event = createDragEvent({
            currentTarget: container,
            relatedTarget: relatedTarget,
        })

        result.onItemDragOver(event, 1)
        result.onLaneDragLeave(event)

        expect(event.currentTarget).toBe(container)
        expect(event.relatedTarget).toBe(relatedTarget)
        expect(result.dragOverIndex.value).toBeNull()

        wrapper.unmount()
    })

    it("clears cue when relatedTarget is null", () => {
        const canDropIn = vi.fn((_parentPath: string) => true)
        const {wrapper, result} = mountLaneDrag(
            createDragContext({canDropIn}),
        )
        
        const container = document.createElement("div")
        const event = createDragEvent({
            currentTarget: container,
            relatedTarget: null,
        })

        result.onItemDragOver(event, 1)
        result.onLaneDragLeave(event)

        expect(event.currentTarget).toBe(container)
        expect(event.relatedTarget).toBeNull()
        expect(result.dragOverIndex.value).toBeNull()

        wrapper.unmount()
    })
})
