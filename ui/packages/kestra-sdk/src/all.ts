// The whole generated API surface behind one specifier. Kept off the root entry, which is a
// module-federation share and would pin every name it exports into the app's initial graph.
import * as sdk from "./openapi/index"

export * from "./openapi/index"
export default sdk
