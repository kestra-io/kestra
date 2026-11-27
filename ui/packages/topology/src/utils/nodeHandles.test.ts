import {describe, expect, it} from "vitest"
import {Position} from "@vue-flow/core"
import {statusRowHandleStyle} from "./nodeHandles"
import {NODE_SIZES} from "./constants"

describe("statusRowHandleStyle", () => {
    it("shouldLiftABottomHandleToTheCardEdgeAboveTheReservedRow", () => {
        expect(statusRowHandleStyle(Position.Bottom, true)).toEqual({bottom: `${NODE_SIZES.TASK_STATUS_ROW_HEIGHT}px`})
    })

    it("shouldCentreASideHandleOnTheCard", () => {
        expect(statusRowHandleStyle(Position.Right, true)).toEqual({top: `calc(50% - ${NODE_SIZES.TASK_STATUS_ROW_HEIGHT / 2}px)`})
        expect(statusRowHandleStyle(Position.Left, true)).toEqual({top: `calc(50% - ${NODE_SIZES.TASK_STATUS_ROW_HEIGHT / 2}px)`})
    })

    it("shouldLeaveATopHandleAlone", () => {
        expect(statusRowHandleStyle(Position.Top, true)).toBeUndefined()
    })

    it("shouldLeaveEveryHandleAloneWhenNoRowIsReserved", () => {
        expect(statusRowHandleStyle(Position.Bottom, false)).toBeUndefined()
        expect(statusRowHandleStyle(Position.Right, false)).toBeUndefined()
    })
})
