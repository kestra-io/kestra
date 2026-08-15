import {defineAsyncComponent} from "vue"
import type KsMarkdownSfc from "./KsMarkdown.vue"

// Async on purpose: KsMarkdown pulls the whole markdown/Shiki toolchain, which must stay
// out of the app's eager bundle (see the "markdown" chunk group). This module is what the
// barrel and the auto-import resolver both point at, so no consumer can reach the SFC —
// and Shiki — through a static import by accident.
//
// It imports nothing but Vue: this file lands in its own chunk (consolidateChunks keeps
// async boundaries out of every group), and an import back into the design-system chunk
// would make the two chunks import each other and throw on first evaluation.
//
// defineAsyncComponent names its wrapper "AsyncComponentWrapper"; keeping the real name
// lets consumers stub the component by name in tests and read it in devtools.
export default Object.assign(
    defineAsyncComponent(() => import("./KsMarkdown.vue")),
    {name: "KsMarkdown"},
) as unknown as typeof KsMarkdownSfc
