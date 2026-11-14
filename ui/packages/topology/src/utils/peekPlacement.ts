import {NODE_DETAILS_PEEK} from "./constants"

export interface PeekRect {
    x: number;
    y: number;
    width: number;
    height: number;
}

export type PeekSide = "right" | "left" | "below" | "above"

export interface PeekPlacement {
    x: number;
    y: number;
    side: PeekSide;
}

export interface PlacePeekArgs {
    anchor: PeekRect;
    panel: {width: number; height: number};
    canvas: PeekRect;
    isHorizontal: boolean;
    otherRects?: PeekRect[];
    gap?: number;
}

const SIDES_PREFERRED_IN_VERTICAL_FLOW: readonly PeekSide[] = ["right", "left", "below", "above"]
const SIDES_PREFERRED_IN_HORIZONTAL_FLOW: readonly PeekSide[] = ["below", "above", "right", "left"]

function sideOrigin(side: PeekSide, anchor: PeekRect, panel: {width: number; height: number}, gap: number) {
    const centreX = anchor.x + anchor.width / 2 - panel.width / 2
    const centreY = anchor.y + anchor.height / 2 - panel.height / 2
    switch (side) {
    case "right":
        return {x: anchor.x + anchor.width + gap, y: centreY}
    case "left":
        return {x: anchor.x - gap - panel.width, y: centreY}
    case "below":
        return {x: centreX, y: anchor.y + anchor.height + gap}
    case "above":
        return {x: centreX, y: anchor.y - gap - panel.height}
    }
}

function clamp(value: number, min: number, max: number) {
    return Math.max(min, Math.min(value, Math.max(min, max)))
}

function overlapArea(a: PeekRect, b: PeekRect) {
    const width = Math.min(a.x + a.width, b.x + b.width) - Math.max(a.x, b.x)
    const height = Math.min(a.y + a.height, b.y + b.height) - Math.max(a.y, b.y)
    return width > 0 && height > 0 ? width * height : 0
}

export function placePeek({anchor, panel, canvas, isHorizontal, otherRects = [], gap = NODE_DETAILS_PEEK.GAP_PX}: PlacePeekArgs): PeekPlacement {
    const sides = isHorizontal ? SIDES_PREFERRED_IN_HORIZONTAL_FLOW : SIDES_PREFERRED_IN_VERTICAL_FLOW
    const rectsThePanelMustNotCover = [anchor, ...otherRects]

    const candidates = sides.map((side) => {
        const origin = sideOrigin(side, anchor, panel, gap)
        const x = clamp(origin.x, canvas.x, canvas.x + canvas.width - panel.width)
        const y = clamp(origin.y, canvas.y, canvas.y + canvas.height - panel.height)
        const covered = rectsThePanelMustNotCover.reduce(
            (total, other) => total + overlapArea({x, y, width: panel.width, height: panel.height}, other),
            0,
        )
        return {x, y, side, covered}
    })

    const chosen = candidates.reduce((best, candidate) => (candidate.covered < best.covered ? candidate : best))
    return {x: chosen.x, y: chosen.y, side: chosen.side}
}
