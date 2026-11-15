import { describe, it, expect, vi } from "vitest";
import { defineComponent, provide, ref } from "vue";
import { mount } from "@vue/test-utils";

import { useLaneDrag } from "./useLaneDrag";
import { BLOCK_DRAG_INJECTION_KEY } from "../injectionKeys";

import type { BlockDragContext } from "./useBlockDragAndDrop";

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
    };
}

describe("useLaneDrag", () => {
    it("sets dropState based on whether the lane accepts the drop", () => {
        const draggedPath = ref<string | null>(null);
        const canDropIn = vi.fn((_parentPath: string) => true);

        let result: ReturnType<typeof useLaneDrag>;

        const wrapper = mount(
            defineComponent({
                setup() {
                    provide(
                        BLOCK_DRAG_INJECTION_KEY,
                        createDragContext({ draggedPath, canDropIn }),
                    );

                    result = useLaneDrag(
                        () => "blocks",
                        () => 2,
                    );

                    return () => null;
                },
            }),
        );

        // Nothing is being dragged, so the state should be idle.
        expect(result!.dropState.value).toBe("idle");

        // Start dragging an item.
        draggedPath.value = "blocks[0]";

        // The lane accepts the drop.
        expect(result!.dropState.value).toBe("allowed");
        expect(canDropIn).toHaveBeenCalledWith("blocks");

        // The lane refuses the drop.
        canDropIn.mockReturnValue(false);
        draggedPath.value = "blocks[1]";

        expect(result!.dropState.value).toBe("forbidden");

        wrapper.unmount();
    });

    it("sets dropState as idle when no dragContext is injected", () => {
        let result: ReturnType<typeof useLaneDrag>;

        const wrapper = mount(
            defineComponent({
                setup() {
                    result = useLaneDrag(
                        () => "blocks",
                        () => 2,
                    );

                    return () => null;
                },
            }),
        );

        expect(result!.dropState.value).toBe("idle");

        wrapper.unmount();
    });

    it("begins a drag at lane path with the item index", () => {
        const beginDrag = vi.fn((_parentPath: string) => {});

        let result: ReturnType<typeof useLaneDrag>;

        const wrapper = mount(
            defineComponent({
                setup() {
                    provide(
                        BLOCK_DRAG_INJECTION_KEY,
                        createDragContext({ beginDrag }),
                    );

                    result = useLaneDrag(
                        () => "blocks",
                        () => 2,
                    );

                    return () => null;
                },
            }),
        );

        const event = {} as DragEvent;

        result!.onItemDragStart(event, 1);
        expect(beginDrag).toHaveBeenCalledWith("blocks[1]");
        wrapper.unmount();
    });

    it("calls dropAt with lane path and index when dropped on an accepting lane", () => {
        const canDropIn = vi.fn((_parentPath: string) => true);
        const dropAt = vi.fn((_parentPath: string, _index: number) => {});

        let result: ReturnType<typeof useLaneDrag>;

        const wrapper = mount(
            defineComponent({
                setup() {
                    provide(
                        BLOCK_DRAG_INJECTION_KEY,
                        createDragContext({ canDropIn }),
                    );

                    result = useLaneDrag(
                        () => "blocks",
                        () => 2,
                    );

                    return () => null;
                },
            }),
        );

        const event = {} as DragEvent;

        result!.onItemDrop(event, 1);
        expect(canDropIn).toHaveBeenCalledWith("blocks");
        expect(dropAt).toHaveBeenCalledWith("blocks", 1);
        expect(event.preventDefault).toHaveBeenCalledOnce();
        expect(event.stopPropagation).toHaveBeenCalledOnce();

        wrapper.unmount();
    });
});
