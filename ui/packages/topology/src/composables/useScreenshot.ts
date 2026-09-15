import {ref} from "vue"
import type {Ref} from "vue"
import type {Rect} from "@vue-flow/core"
import {toJpeg as ElToJpg, toPng as ElToPng} from "html-to-image"
import type {Options as HTMLToImageOptions} from "html-to-image/es/types"

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
    transform: `translate(${EXPORT_PADDING - bounds.x * zoom}px, ${EXPORT_PADDING - bounds.y * zoom}px) scale(${zoom})`,
  }
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
    const {type, fileName = `flow-graph-${Date.now()}`, shouldDownload, bounds, ...imageOptions} = options

    // The pane is rendered with its own translate/scale so the image can extend past the
    // viewport; it has no background of its own, so the container's goes on the canvas.
    const pane = el.querySelector<HTMLElement>(".vue-flow__transformationpane")
    let target = el
    if (pane && isExportable(bounds)) {
      target = pane
      const {transform, ...geometry} = exportGeometry(bounds, window.devicePixelRatio)
      Object.assign(imageOptions, {
        ...geometry,
        backgroundColor: imageOptions.backgroundColor ?? getComputedStyle(el).backgroundColor,
        style: {...imageOptions.style, transform},
      })
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
      const data = type === "jpeg"
        ? await toJpeg(target, imageOptions)
        : await toPng(target, imageOptions)

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
      .then((data) => {
        dataUrl.value = data
        imgType.value = "jpeg"
        return data
      })
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
      .then((data) => {
        dataUrl.value = data
        imgType.value = "png"
        return data
      })
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
