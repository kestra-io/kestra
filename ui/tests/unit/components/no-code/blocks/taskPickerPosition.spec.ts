import {describe, it, expect} from "vitest"
import {computePickerPosition} from "../../../../../src/components/no-code/blocks/taskPickerPosition"

const VIEWPORT = {width: 1280, height: 720}

describe("computePickerPosition", () => {
    it("opens below the anchor when there is room", () => {
        // Given — a card near the top of a tall viewport
        const anchor = {top: 100, bottom: 152, left: 320}

        // When
        const position = computePickerPosition(anchor, VIEWPORT)

        // Then
        expect(position.top).toBe("156px")
        expect(position.bottom).toBeUndefined()
        expect(position.left).toBe("320px")
    })

    it("opens above once the space below is too tight and above is roomier", () => {
        // Given — a card low in the viewport
        const anchor = {top: 600, bottom: 652, left: 320}

        // When
        const position = computePickerPosition(anchor, VIEWPORT)

        // Then
        expect(position.bottom).toBe("124px")
        expect(position.top).toBeUndefined()
    })

    it("keeps the popover on screen when the anchor scrolled far below the fold", () => {
        // Given — the anchor sits well past the bottom edge
        const anchor = {top: 1400, bottom: 1452, left: 320}

        // When
        const position = computePickerPosition(anchor, VIEWPORT)

        // Then — pinned inside the viewport rather than following the anchor off screen
        const bottom = Number.parseFloat(position.bottom!)
        const maxHeight = Number.parseFloat(position.maxHeight)
        expect(bottom).toBeGreaterThanOrEqual(8)
        expect(bottom + maxHeight).toBeLessThanOrEqual(VIEWPORT.height)
    })

    it("keeps the popover on screen when the anchor scrolled above the fold", () => {
        // Given — the anchor sits above the viewport
        const anchor = {top: -500, bottom: -448, left: 320}

        // When
        const position = computePickerPosition(anchor, VIEWPORT)

        // Then
        const top = Number.parseFloat(position.top!)
        const maxHeight = Number.parseFloat(position.maxHeight)
        expect(top).toBeGreaterThanOrEqual(8)
        expect(top + maxHeight).toBeLessThanOrEqual(VIEWPORT.height)
    })

    it("clamps a negative anchor left to the viewport margin", () => {
        // Given
        const anchor = {top: 100, bottom: 152, left: -40}

        // When
        const position = computePickerPosition(anchor, VIEWPORT)

        // Then
        expect(position.left).toBe("8px")
    })

    it("pulls the popover left rather than overflowing the right edge", () => {
        // Given — an anchor close enough to the right edge that the minimum width would overflow
        const anchor = {top: 100, bottom: 152, left: 1200}

        // When
        const position = computePickerPosition(anchor, VIEWPORT)

        // Then — left + minWidth stays inside the right margin
        expect(position.left).toBe("992px")
        expect(position.width).toBe("280px")
    })

    it("shrinks the width to fit a narrow viewport but never below the minimum", () => {
        // Given
        const anchor = {top: 100, bottom: 152, left: 40}

        // When
        const narrow = computePickerPosition(anchor, {width: 375, height: 812})

        // Then
        expect(Number.parseFloat(narrow.width)).toBeGreaterThanOrEqual(280)
        expect(Number.parseFloat(narrow.left) + Number.parseFloat(narrow.width)).toBeLessThanOrEqual(375)
    })

    it("caps the height at the maximum and floors it at the minimum", () => {
        // Given — plenty of room below, then almost none anywhere
        const roomy = computePickerPosition({top: 40, bottom: 60, left: 320}, {width: 1280, height: 1400})
        const cramped = computePickerPosition({top: 140, bottom: 180, left: 320}, {width: 1280, height: 300})

        // Then
        expect(Number.parseFloat(roomy.maxHeight)).toBe(420)
        expect(Number.parseFloat(cramped.maxHeight)).toBe(200)
    })

    it("follows the anchor as it moves, which is what a scroll does", () => {
        // Given — the same card before and after the canvas scrolls up by 170px
        const before = computePickerPosition({top: 418, bottom: 470, left: 324}, VIEWPORT)
        const after = computePickerPosition({top: 248, bottom: 300, left: 324}, VIEWPORT)

        // Then — the stale-position bug: before opens up, after must open down
        expect(before.bottom).toBeDefined()
        expect(after.top).toBe("304px")
        expect(after.bottom).toBeUndefined()
    })
})
