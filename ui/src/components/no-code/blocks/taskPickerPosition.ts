export interface AnchorRect {
    top: number
    bottom: number
    left: number
}

export interface Viewport {
    width: number
    height: number
}

export interface PickerPosition {
    left: string
    width: string
    maxHeight: string
    top?: string
    bottom?: string
}

const GAP = 4
const MARGIN = 8
const MAX_HEIGHT = 420
const PREFERRED_WIDTH = 440
const MIN_WIDTH = 280
const MIN_HEIGHT = 200
const OPEN_DOWN_THRESHOLD = 280

export function computePickerPosition(anchor: AnchorRect, viewport: Viewport): PickerPosition {
    const maxRight = viewport.width - MARGIN
    const left = Math.max(MARGIN, Math.min(anchor.left, maxRight - MIN_WIDTH))
    const width = Math.max(MIN_WIDTH, Math.min(PREFERRED_WIDTH, maxRight - left))

    const spaceBelow = viewport.height - anchor.bottom - GAP - MARGIN
    const spaceAbove = anchor.top - GAP - MARGIN
    const opensUp = spaceBelow < Math.min(MAX_HEIGHT, OPEN_DOWN_THRESHOLD) && spaceAbove > spaceBelow
    const maxHeight = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, opensUp ? spaceAbove : spaceBelow))

    const edge = opensUp
        ? {bottom: clampToViewport(viewport.height - anchor.top + GAP, viewport.height, maxHeight)}
        : {top: clampToViewport(anchor.bottom + GAP, viewport.height, maxHeight)}

    return {left: `${left}px`, width: `${width}px`, maxHeight: `${maxHeight}px`, ...edge}
}

function clampToViewport(offset: number, viewportHeight: number, maxHeight: number): string {
    const highest = Math.max(MARGIN, viewportHeight - maxHeight - MARGIN)
    return `${Math.min(Math.max(offset, MARGIN), highest)}px`
}
