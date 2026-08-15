import {defineAsyncComponent} from "vue"
import type KsEditorSfc from "./KsEditor.vue"

// Async on purpose: KsEditor statically pulls the whole Monaco toolchain, which must stay
// out of the app's eager bundle (see the "monaco" chunk group). This module is what the
// barrel and the auto-import resolver both point at, so no consumer can reach the SFC —
// and Monaco — through a static import by accident.
//
// It imports nothing but Vue: this file lands in its own chunk (consolidateChunks keeps
// async boundaries out of every group), and an import back into the design-system chunk
// would make the two chunks import each other and throw on first evaluation.
//
// defineAsyncComponent names its wrapper "AsyncComponentWrapper"; keeping the real name
// lets consumers stub the component by name in tests and read it in devtools.
export default Object.assign(
    defineAsyncComponent(() => import("./KsEditor.vue")),
    {name: "KsEditor"},
) as unknown as typeof KsEditorSfc
