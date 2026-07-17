import {defineAsyncComponent} from "vue"

/**
 * Async wrapper around the markdown renderer: KsMarkdownContent drags in
 * unified/remark, xss and the Shiki grammars — several hundred KB that must
 * not ride into the boot bundle of every design-system consumer. The real
 * component loads as its own chunk on first markdown render.
 */
export default defineAsyncComponent(() => import("./KsMarkdownContent.vue"))
