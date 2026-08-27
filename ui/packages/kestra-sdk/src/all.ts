// The whole generated API surface behind one specifier, for consumers who prefer a single import
// over the per-tag subpaths. This is the only place the barrel lives: the root entry deliberately
// exports types only, because it is a module-federation share and re-exporting the operations
// there pinned every one of them into the app's initial graph.
import * as sdk from "./openapi/index"

export * from "./openapi/index"
export default sdk
