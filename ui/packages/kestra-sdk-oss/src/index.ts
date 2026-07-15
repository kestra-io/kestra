import { client } from "./openapi/client.gen"
import { createConfigureClient } from "./client-setup"

export * from "./openapi/index"
export { useClient, setMockClient } from "./client-setup"

export const configureClient = createConfigureClient(client)
