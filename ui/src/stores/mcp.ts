import * as McpAPI from "@kestra-io/kestra-sdk/mcp"
import type {
    ApiMcpServer,
    ApiMcpServerWritable,
    McpServerAuthType,
    McpServerControllerApiMcpTool,
    McpServerControllerApiMcpToolAnnotations,
    PagedResultsApiMcpServer,
} from "@kestra-io/kestra-sdk"
import {defineStore} from "pinia"
import {ref} from "vue"

// The generated types mark these fields optional (OpenAPI doesn't express that
// the backend always populates them on responses/requires them on writes),
// so re-narrow them to match what the API actually guarantees.
export type McpServer = ApiMcpServer & Pick<Required<ApiMcpServer>, "serverType" | "authType" | "disabled" | "isDefault">
export type McpServerPayload = ApiMcpServerWritable & Pick<Required<ApiMcpServerWritable>, "serverType" | "authType" | "disabled">
export {McpServerAuthType}
export type McpToolAnnotations = Required<McpServerControllerApiMcpToolAnnotations>
export type McpTool = Required<McpServerControllerApiMcpTool> & {annotations: McpToolAnnotations}

export const useMcpStore = defineStore("mcp", () => {
    const server = ref<McpServer | null>(null)

    const list = async (): Promise<{results: McpServer[], total: number}> => {
        return McpAPI.listMcps() as Promise<PagedResultsApiMcpServer & {results: McpServer[], total: number}>
    }

    const load = async (id: string): Promise<void> => {
        try {
            server.value = await McpAPI.mcp({id}) as McpServer
        } catch {
            server.value = null
        }
    }

    const create = async (payload: McpServerPayload): Promise<McpServer> => {
        return McpAPI.createMcp(payload) as Promise<McpServer>
    }

    const update = async (id: string, payload: McpServerPayload): Promise<McpServer> => {
        const {id: _payloadId, ...rest} = payload
        return McpAPI.updateMcp({id, ...rest}) as Promise<McpServer>
    }

    const remove = async (id: string): Promise<void> => {
        await McpAPI.deleteMcp({id})
    }

    const toggle = async (id: string): Promise<McpServer> => {
        return McpAPI.toggleMcp({id}) as Promise<McpServer>
    }

    const listTools = async (id: string): Promise<McpTool[]> => {
        return McpAPI.listTools({id}) as Promise<McpTool[]>
    }

    return {server, list, load, create, update, remove, toggle, listTools}
})
