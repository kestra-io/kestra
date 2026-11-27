export type Hole = {top: number; left: number; bottom: number; right: number}

export type Viewport = {width: number; height: number}

export const scrimClipPath = (hole: Hole, radius: number, viewport: Viewport): string => {
    const width = hole.right - hole.left
    const height = hole.bottom - hole.top
    const r = Math.min(radius, width / 2, height / 2)
    const {top, left, bottom, right} = hole

    return `path("M0,0 L${viewport.width},0 L${viewport.width},${viewport.height} L0,${viewport.height} Z `
        + `M${left + r},${top} `
        + `A${r},${r} 0 0 0 ${left},${top + r} `
        + `L${left},${bottom - r} `
        + `A${r},${r} 0 0 0 ${left + r},${bottom} `
        + `L${right - r},${bottom} `
        + `A${r},${r} 0 0 0 ${right},${bottom - r} `
        + `L${right},${top + r} `
        + `A${r},${r} 0 0 0 ${right - r},${top} Z")`
}
