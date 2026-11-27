import {Position} from "@vue-flow/core"
import {NODE_SIZES} from "./constants"

export function statusRowHandleStyle(position: Position | undefined, reservesRow: boolean) {
    if (!reservesRow) return undefined
    if (position === Position.Left || position === Position.Right) {
        return {top: `calc(50% - ${NODE_SIZES.TASK_STATUS_ROW_HEIGHT / 2}px)`}
    }
    if (position === Position.Bottom) {
        return {bottom: `${NODE_SIZES.TASK_STATUS_ROW_HEIGHT}px`}
    }
    return undefined
}
