import {ref, Ref} from "vue";
import {toJpeg as ElToJpg, toPng as ElToPng} from "html-to-image";
import type {Options as HTMLToImageOptions} from "html-to-image/es/types";

type ImageType = "jpeg" | "png";

interface UseScreenshotOptions extends HTMLToImageOptions {
  type?: ImageType;
  fileName?: string;
  shouldDownload?: boolean;
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

export function useScreenshot(): UseScreenshot {
  const dataUrl = ref<string>("");
  const imgType = ref<ImageType>("png");
  const error = ref();

  async function capture(el: HTMLElement, options: UseScreenshotOptions = {}) {
    const fileName = options.fileName ?? `flow-graph-${Date.now()}`;

    el.classList.add("is-exporting");

    const edgePaths = el.querySelectorAll(".vue-flow__edge-path");
    const originalStyles = Array.from(edgePaths).map(edge => {
      const element = edge as HTMLElement;
      const original = element.getAttribute("style") || "";

      const computed = window.getComputedStyle(element);

      element.setAttribute(
        "style",
        `
        ${original};
        stroke: ${computed.stroke};
        stroke-width: ${computed.strokeWidth};
        stroke-dasharray: ${computed.strokeDasharray};
        fill: none;
        `
      );

      return {element, original};
    });

    try {
      const data = options.type === "jpeg"
        ? await toJpeg(el, options)
        : await toPng(el, options);

      if (options.shouldDownload && fileName) download(fileName);
      return data;
    } finally {
      originalStyles.forEach(({element, original}) =>
        original ? element.setAttribute("style", original) : element.removeAttribute("style")
      );
      el.classList.remove("is-exporting");
    }
  }

  function toJpeg(
    el: HTMLElement,
    options: HTMLToImageOptions = {quality: 0.95}
  ) {
    error.value = null;

    return ElToJpg(el, options)
      .then((data) => {
        dataUrl.value = data;
        imgType.value = "jpeg";
        return data;
      })
      .catch((error) => {
        error.value = error;
        throw new Error(error);
      });
  }

  function toPng(
    el: HTMLElement,
    options: HTMLToImageOptions = {quality: 0.95}
  ) {
    error.value = null;

    return ElToPng(el, options)
      .then((data) => {
        dataUrl.value = data;
        imgType.value = "png";
        return data;
      })
      .catch((error) => {
        error.value = error;
        throw new Error(error);
      });
  }

  function download(fileName: string) {
    const link = document.createElement("a");
    link.download = `${fileName}.${imgType.value}`;
    link.href = dataUrl.value;
    link.click();
  }

  return {
    capture,
    download,
    dataUrl,
    error,
  };
}
