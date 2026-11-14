import {describe, it, expect, vi} from "vitest"
import {defineComponent, provide, ref} from "vue"
import {mount} from "@vue/test-utils"

import {useLaneDrag} from "./useLaneDrag"
import {BLOCK_DRAG_INJECTION_KEY} from "../injectionKeys"

describe("useLaneDrag", () => {
    it("sets dropState based on whether the lane accepts the drop", () => {
        const draggedPath = ref<string | null>(null)
        const canDropIn = vi.fn().mockReturnValue(true)

        let result: ReturnType<typeof useLaneDrag>

        const wrapper = mount(defineComponent({
            setup() {
                provide(BLOCK_DRAG_INJECTION_KEY, {
                    draggedPath,
                    canDropIn,
                } as any)

                result = useLaneDrag(() => "blocks", () => 2)

                return () => null
            }
        }))

        // Nothing is being dragged, so the state should be idle.
        expect(result!.dropState.value).toBe("idle")

        // Start dragging an item.
        draggedPath.value = "blocks[0]"

        // The lane accepts the drop.
        expect(result!.dropState.value).toBe("allowed")
        expect(canDropIn).toHaveBeenCalledWith("blocks")

        // The lane refuses the drop.
        canDropIn.mockReturnValue(false)
        draggedPath.value = "blocks[1]"

        expect(result!.dropState.value).toBe("forbidden")

        wrapper.unmount()
    })
  
  it("dropState is idle when no dragContext is injected", () => {
    let result: ReturnType<typeof useLaneDrag>

    const wrapper = mount(defineComponent({
      setup() {
        result = useLaneDrag(() => "blocks", () => 2)
        
        return () => null
      }
    }))

    expect(result!.dropState.value).toBe("idle")
  })
})
