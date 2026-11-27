export type Rect = {top: number; left: number; bottom: number; right: number}

export type Size = {width: number; height: number}

export const CORNERS = ["bottom-right", "bottom-left", "top-right", "top-left"] as const

export type Corner = typeof CORNERS[number]

export const cornerOrder = (placement?: "left"): Corner[] =>
    placement === "left"
        ? ["bottom-left", "bottom-right", "top-left", "top-right"]
        : [...CORNERS]

export const cornerRect = (corner: Corner, card: Size, area: Rect): Rect => {
    const top = corner.startsWith("top") ? area.top : area.bottom - card.height
    const left = corner.endsWith("left") ? area.left : area.right - card.width
    return {top, left, bottom: top + card.height, right: left + card.width}
}

export const shiftRect = (rect: Rect, offset: {x: number; y: number}): Rect => ({
    top: rect.top + offset.y,
    left: rect.left + offset.x,
    bottom: rect.bottom + offset.y,
    right: rect.right + offset.x,
})

const overlaps = (a: Rect, b: Rect) =>
    a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

// Not weighed by area: on a target filling the page, the smallest overlap is the corner
// sitting on its header, which is the worst one to cover.
export const coverScore = (card: Rect, targets: Rect[], avoid: Rect[]) =>
    (avoid.some((rect) => overlaps(card, rect)) ? 2 : 0)
    + (targets.some((rect) => overlaps(card, rect)) ? 1 : 0)

/** The corner of `area` with the lowest cover score; a tie goes to the earliest in `order`. */
export const bestCorner = (
    card: Size,
    area: Rect,
    targets: Rect[],
    avoid: Rect[],
    order: readonly Corner[] = CORNERS,
) =>
    order
        .map((corner) => ({corner, score: coverScore(cornerRect(corner, card, area), targets, avoid)}))
        .reduce((best, candidate) => (candidate.score < best.score ? candidate : best))
