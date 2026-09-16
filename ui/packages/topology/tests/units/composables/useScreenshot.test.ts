import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"

const {toPng, toJpeg} = vi.hoisted(() => ({
    toPng: vi.fn(),
    toJpeg: vi.fn(),
}))

vi.mock("html-to-image", () => ({toPng, toJpeg}))

import {EXPORT_PADDING, MAX_EXPORT_SIZE, exportGeometry, untilNodesMeasured, useScreenshot} from "../../../src/composables/useScreenshot"

describe("exportGeometry", () => {
    it("frames a graph that fits the budget at its on-screen size and device resolution", () => {
        const geometry = exportGeometry({x: -50, y: 20, width: 400, height: 300}, 2)

        expect(geometry).toEqual({
            width: 400 + 2 * EXPORT_PADDING,
            height: 300 + 2 * EXPORT_PADDING,
            pixelRatio: 2,
            zoom: 1,
            transform: `translate(${EXPORT_PADDING + 50}px, ${EXPORT_PADDING - 20}px) scale(1)`,
        })
    })

    it("scales a graph wider than the budget down to the longest allowed side", () => {
        const zoom = (MAX_EXPORT_SIZE - 2 * EXPORT_PADDING) / 20000
        const geometry = exportGeometry({x: 0, y: 0, width: 20000, height: 1000}, 2)

        expect(geometry.width).toBe(MAX_EXPORT_SIZE)
        expect(geometry.height).toBe(Math.ceil(1000 * zoom) + 2 * EXPORT_PADDING)
        expect(geometry.pixelRatio).toBe(1)
        expect(geometry.transform).toBe(`translate(${EXPORT_PADDING}px, ${EXPORT_PADDING}px) scale(${zoom})`)
    })

    it("lowers the device resolution before it lowers the zoom", () => {
        const geometry = exportGeometry({x: 0, y: 0, width: 6000, height: 100}, 2)

        expect(geometry.width).toBe(6000 + 2 * EXPORT_PADDING)
        expect(geometry.transform).toContain("scale(1)")
        expect(geometry.pixelRatio).toBeGreaterThan(1)
        expect(geometry.pixelRatio).toBeLessThan(2)
        expect(geometry.width * geometry.pixelRatio).toBeLessThanOrEqual(MAX_EXPORT_SIZE)
    })
})

describe("untilNodesMeasured", () => {
    const measured = {dimensions: {width: 100, height: 40}}
    const unmeasured = {dimensions: {width: 0, height: 0}}

    it("resolves once every rendered node carries dimensions", async () => {
        const nodes = [measured, unmeasured]

        const waited = untilNodesMeasured(() => nodes)
        nodes[1] = measured

        await expect(waited).resolves.toBeUndefined()
    })

    it("resolves straight away for a graph with no nodes", async () => {
        await expect(untilNodesMeasured(() => [])).resolves.toBeUndefined()
    })

    it("gives up after its frame budget so a node that never measures cannot block the export", async () => {
        await expect(untilNodesMeasured(() => [unmeasured], 3)).resolves.toBeUndefined()
    })
})

describe("useScreenshot", () => {
    let container: HTMLElement
    let pane: HTMLElement
    let painted: {dots: number; fills: string[]; encodedAs: string[]}

    // jsdom neither decodes an image nor draws on a canvas, so both are stood in for: the capture
    // resolves at once, and the 2d context records what the composite asked it to paint.
    function stubCanvasAndImage() {
        painted = {dots: 0, fills: [], encodedAs: []}

        vi.stubGlobal("Image", class {
            onload: (() => void) | null = null
            onerror: (() => void) | null = null
            width = 200
            height = 100
            set src(_value: string) {
                queueMicrotask(() => this.onload?.())
            }
        })

        vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockImplementation(function (this: HTMLCanvasElement) {
            return {
                canvas: this,
                set fillStyle(value: string) {
                    painted.fills.push(value)
                },
                fillRect: vi.fn(),
                beginPath: vi.fn(),
                arc: () => {
                    painted.dots += 1
                },
                fill: vi.fn(),
                drawImage: vi.fn(),
            } as unknown as CanvasRenderingContext2D
        })
        vi.spyOn(HTMLCanvasElement.prototype, "toDataURL").mockImplementation((type?: string) => {
            painted.encodedAs.push(type ?? "image/png")
            return `composited:${type}`
        })
    }

    beforeEach(() => {
        toPng.mockReset().mockResolvedValue("data:image/png;base64,png")
        toJpeg.mockReset().mockResolvedValue("data:image/jpeg;base64,jpeg")
        stubCanvasAndImage()

        // Appended and removed on its own: the unit project shares one jsdom per worker, and
        // clearing the whole body would take Element Plus's popper container with it.
        container = document.createElement("div")
        container.className = "vue-flow"
        container.style.backgroundColor = "rgb(1, 2, 3)"
        pane = document.createElement("div")
        pane.className = "vue-flow__transformationpane"
        container.append(pane)
        document.body.append(container)
    })

    afterEach(() => {
        container.remove()
        vi.unstubAllGlobals()
        vi.restoreAllMocks()
    })

    it("captures the whole graph off the transformed pane, transparent, and composites it", async () => {
        const {capture} = useScreenshot()

        const data = await capture(container, {type: "png", bounds: {x: 10, y: 20, width: 100, height: 50}})

        expect(toPng).toHaveBeenCalledTimes(1)
        expect(toPng).toHaveBeenCalledWith(pane, {
            width: 100 + 2 * EXPORT_PADDING,
            height: 50 + 2 * EXPORT_PADDING,
            pixelRatio: 1,
            // The ground is painted by the composite, so the capture itself carries none.
            backgroundColor: undefined,
            style: {transform: `translate(${EXPORT_PADDING - 10}px, ${EXPORT_PADDING - 20}px) scale(1)`},
        })
        expect(data).toBe("composited:image/png")
        expect(painted.fills[0]).toBe("rgb(1, 2, 3)")
        expect(painted.dots).toBeGreaterThan(0)
        expect(container.classList.contains("is-exporting")).toBe(false)
    })

    it("captures as png for a jpeg export, then re-encodes the composite as jpeg", async () => {
        const {capture} = useScreenshot()

        const data = await capture(container, {type: "jpeg", bounds: {x: 0, y: 0, width: 10, height: 10}})

        expect(toJpeg).not.toHaveBeenCalled()
        expect(toPng).toHaveBeenCalledTimes(1)
        expect(painted.encodedAs).toEqual(["image/jpeg"])
        expect(data).toBe("composited:image/jpeg")
    })

    it("keeps the caller's background colour and style over the derived ones", async () => {
        const {capture} = useScreenshot()

        await capture(container, {
            type: "png",
            bounds: {x: 0, y: 0, width: 10, height: 10},
            backgroundColor: "white",
            style: {opacity: "0.5"},
        })

        expect(toPng).toHaveBeenCalledWith(pane, expect.objectContaining({
            style: {opacity: "0.5", transform: `translate(${EXPORT_PADDING}px, ${EXPORT_PADDING}px) scale(1)`},
        }))
        expect(painted.fills[0]).toBe("white")
    })

    it("falls back to the on-screen container when the bounds are unusable", async () => {
        const {capture} = useScreenshot()

        // `getRectOfNodes([])`: no node, so no finite box.
        await capture(container, {type: "png", bounds: {x: Infinity, y: Infinity, width: -Infinity, height: -Infinity}})
        await capture(container, {type: "png"})

        expect(toPng).toHaveBeenCalledTimes(2)
        expect(toPng).toHaveBeenNthCalledWith(1, container, {})
        expect(toPng).toHaveBeenNthCalledWith(2, container, {})
        expect(painted.dots).toBe(0)
    })

    it("hides the interactive chrome while capturing and restores it afterwards", async () => {
        const {capture} = useScreenshot()
        let exportingDuringCapture: boolean | undefined
        toPng.mockImplementation(async () => {
            exportingDuringCapture = container.classList.contains("is-exporting")
            return "data:image/png;base64,png"
        })

        await capture(container, {type: "png", bounds: {x: 0, y: 0, width: 10, height: 10}})

        expect(exportingDuringCapture).toBe(true)
        expect(container.classList.contains("is-exporting")).toBe(false)
    })

    it("downloads the image under the requested name and type", async () => {
        const {capture} = useScreenshot()
        const downloads: string[] = []
        const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) {
            downloads.push(this.download)
        })

        await capture(container, {type: "jpeg", fileName: "graph", shouldDownload: true, bounds: {x: 0, y: 0, width: 10, height: 10}})

        expect(downloads).toEqual(["graph.jpeg"])
        click.mockRestore()
    })
})
