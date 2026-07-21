import { client } from "./openapi/client.gen"
import { createConfigureClient } from "@kestra-io/hey-api-plugin/runtime"
import { rememberInstance } from "./client-setup"

export * from "./openapi/index"
export { useClient, setMockClient } from "./client-setup"

const configure = createConfigureClient(client)

// createConfigureClient (the universal setup) lives in the shared runtime package; the app owns the
// "current instance" singleton (useClient/setMockClient in ./client-setup), so wrap the factory to
// record the instance it builds. The plugin runtime is bundled into this package's dist at build
// time (see tsdown.config), so no runtime dependency is added.
export function configureClient(...args: Parameters<typeof configure>) {
    const instance = configure(...args)
    rememberInstance(instance)
    return instance
}
