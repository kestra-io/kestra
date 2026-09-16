import {ref} from "vue"
import type {Ref} from "vue"
import type {Rect} from "@vue-flow/core"
import {cssVar} from "@kestra-io/design-system"
import {toJpeg as ElToJpg, toPng as ElToPng} from "html-to-image"
import type {Options as HTMLToImageOptions} from "html-to-image/es/types"
import {GRAPH_BACKGROUND} from "../utils/constants"

type ImageType = "jpeg" | "png";

interface UseScreenshotOptions extends HTMLToImageOptions {
  type?: ImageType;
  fileName?: string;
  shouldDownload?: boolean;
  // Bounding box of the graph in flow coordinates (see `getRectOfNodes`). When set, the export
  // covers that whole box instead of whatever part of it the on-screen viewport shows.
  bounds?: Rect;
}

type CaptureScreenshot = (
  el: HTMLElement,
  options?: UseScreenshotOptions
) => Promise<string>;

type Download = (fileName: string) => void;

interface UseScreenshot {
  capture: CaptureScreenshot;
  download: Download;
  dataUrl: Ref<string>;
  error: Ref;
}

interface ExportGeometry {
  width: number;
  height: number;
  pixelRatio: number;
  zoom: number;
  transform: string;
}

// Space kept around the graph in a whole-graph export, in image pixels.
export const EXPORT_PADDING = 32
// Longest side an exported image may have, in device pixels. Stays well below the browsers'
// canvas limit (16384) and keeps the encoding of very large graphs bearable.
export const MAX_EXPORT_SIZE = 8192

// Image size and pane transform showing the whole `bounds` box: nodes at their on-screen size
// when that fits the budget, scaled down otherwise, and at device resolution while it fits too.
export function exportGeometry(bounds: Rect, devicePixelRatio = 1): ExportGeometry {
  const longestSide = Math.max(bounds.width, bounds.height)
  const zoom = Math.min(1, (MAX_EXPORT_SIZE - 2 * EXPORT_PADDING) / longestSide)
  const width = Math.ceil(bounds.width * zoom) + 2 * EXPORT_PADDING
  const height = Math.ceil(bounds.height * zoom) + 2 * EXPORT_PADDING
  const pixelRatio = Math.max(1, Math.min(devicePixelRatio, MAX_EXPORT_SIZE / Math.max(width, height)))

  return {
    width,
    height,
    pixelRatio,
    zoom,
    transform: `translate(${EXPORT_PADDING - bounds.x * zoom}px, ${EXPORT_PADDING - bounds.y * zoom}px) scale(${zoom})`,
  }
}

// Vue Flow's dot grid is a sibling of the graph pane, sized to the on-screen container and
// offset by the live viewport, so it cannot be captured along with a whole-graph export. It is
// redrawn here instead, at the export's own zoom and aligned to the graph's own coordinates.
function paintBackground(context: CanvasRenderingContext2D, bounds: Rect, geometry: ExportGeometry, background: string) {
  const {pixelRatio, zoom} = geometry

  context.fillStyle = background
  context.fillRect(0, 0, context.canvas.width, context.canvas.height)

  const gap = GRAPH_BACKGROUND.gap * zoom * pixelRatio
  const radius = GRAPH_BACKGROUND.size * zoom * pixelRatio / 2
  if (gap < 1 || radius <= 0) {
    return
  }

  // Flow coordinate 0 lands here, so the lattice keeps the same phase as the on-screen one.
  const originX = (EXPORT_PADDING - bounds.x * zoom) * pixelRatio
  const originY = (EXPORT_PADDING - bounds.y * zoom) * pixelRatio

  context.fillStyle = cssVar(GRAPH_BACKGROUND.color)
  for (let x = originX % gap; x < context.canvas.width; x += gap) {
    for (let y = originY % gap; y < context.canvas.height; y += gap) {
      context.beginPath()
      context.arc(x, y, radius, 0, 2 * Math.PI)
      context.fill()
    }
  }
}

// Lays the captured graph, which is transparent, over that ground and re-encodes it.
function composeOnBackground(captured: string, bounds: Rect, geometry: ExportGeometry, background: string, type: ImageType): Promise<string> {
  return new Promise((resolve, reject) => {
    const image = new Image()

    image.onload = () => {
      const canvas = document.createElement("canvas")
      canvas.width = image.width
      canvas.height = image.height

      const context = canvas.getContext("2d")
      // Only a browser without canvas support, where the flat capture is still the right image.
      if (!context) {
        resolve(captured)
        return
      }

      paintBackground(context, bounds, geometry, background)
      context.drawImage(image, 0, 0)
      resolve(canvas.toDataURL(`image/${type}`))
    }
    image.onerror = () => reject(new Error("The exported graph could not be read back for compositing."))

    image.src = captured
  })
}

interface MeasurableNode {
  dimensions: {width: number; height: number};
}

// Vue Flow leaves a node's dimensions at 0 until its ResizeObserver has measured it, and
// `getRectOfNodes` covers such a node with an empty box. Pass only the nodes that are rendered:
// a hidden one is never measured, so waiting on it would always run out of frames.
export function untilNodesMeasured(renderedNodes: () => MeasurableNode[], maxFrames = 60): Promise<void> {
  return new Promise((resolve) => {
    let framesLeft = maxFrames
    const check = (): void => {
      const measured = renderedNodes().every(({dimensions}) => dimensions.width > 0 && dimensions.height > 0)
      if (measured || --framesLeft <= 0) {
        resolve()
      } else {
        requestAnimationFrame(check)
      }
    }
    requestAnimationFrame(check)
  })
}

// `getRectOfNodes([])` yields infinite coordinates, and a graph of unmeasured nodes a 0x0 box.
function isExportable(bounds?: Rect): bounds is Rect {
  return !!bounds
    && Number.isFinite(bounds.width) && Number.isFinite(bounds.height)
    && bounds.width > 0 && bounds.height > 0
}

export function useScreenshot(): UseScreenshot {
  const dataUrl = ref<string>("")
  const imgType = ref<ImageType>("png")
  const error = ref()

  async function capture(el: HTMLElement, options: UseScreenshotOptions = {}) {
    const {type = "png", fileName = `flow-graph-${Date.now()}`, shouldDownload, bounds, ...imageOptions} = options

    // The pane is rendered with its own translate/scale so the image can extend past the
    // viewport. It carries no background, and the grid behind it cannot be captured, so both
    // are painted onto the canvas afterwards - hence a transparent capture here.
    const pane = el.querySelector<HTMLElement>(".vue-flow__transformationpane")
    const geometry = pane && isExportable(bounds) ? exportGeometry(bounds, window.devicePixelRatio) : undefined
    const background = imageOptions.backgroundColor ?? getComputedStyle(el).backgroundColor
    const target = geometry ? pane! : el

    if (geometry) {
      const {transform, zoom: _zoom, ...size} = geometry
      Object.assign(imageOptions, {...size, backgroundColor: undefined, style: {...imageOptions.style, transform}})
    }

    el.classList.add("is-exporting")

    const edgePaths = el.querySelectorAll(".vue-flow__edge-path")
    const originalStyles = Array.from(edgePaths).map(edge => {
      const element = edge as HTMLElement
      const original = element.getAttribute("style") || ""

      const computed = window.getComputedStyle(element)

      element.setAttribute(
        "style",
        `
        ${original};
        stroke: ${computed.stroke};
        stroke-width: ${computed.strokeWidth};
        stroke-dasharray: ${computed.strokeDasharray};
        fill: none;
        `,
      )

      return {element, original}
    })

    try {
      // A composited export is captured as PNG whatever was asked for: JPEG has no transparency
      // to lay over the background, and the canvas re-encodes to the requested type at the end.
      const captured = geometry || type === "png"
        ? await toPng(target, imageOptions)
        : await toJpeg(target, imageOptions)

      const data = geometry && isExportable(bounds)
        ? await composeOnBackground(captured, bounds, geometry, background, type)
        : captured

      dataUrl.value = data
      imgType.value = type

      if (shouldDownload && fileName) download(fileName)
      return data
    } finally {
      originalStyles.forEach(({element, original}) =>
        original ? element.setAttribute("style", original) : element.removeAttribute("style"),
      )
      el.classList.remove("is-exporting")
    }
  }

  function toJpeg(
    el: HTMLElement,
    options: HTMLToImageOptions = {quality: 0.95},
  ) {
    error.value = null

    return ElToJpg(el, options)
      .catch((err) => {
        error.value = err
        throw new Error(err)
      })
  }

  function toPng(
    el: HTMLElement,
    options: HTMLToImageOptions = {quality: 0.95},
  ) {
    error.value = null

    return ElToPng(el, options)
      .catch((err) => {
        error.value = err
        throw new Error(err)
      })
  }

  function download(fileName: string) {
    const link = document.createElement("a")
    link.download = `${fileName}.${imgType.value}`
    link.href = dataUrl.value
    link.click()
  }

  return {
    capture,
    download,
    dataUrl,
    error,
  }
}
