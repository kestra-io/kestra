import {useClient} from "@kestra-io/kestra-sdk"
import {defineStore} from "pinia"
import {ref} from "vue"
import {apiUrl} from "override/utils/route"

export type McpAuthType = "BASIC" | "API_TOKEN" | "OAUTH";

export interface McpServer {
    id: string;
    description?: string;
    instructions?: string;
    serverType: "PRIVATE" | "PUBLIC";
    authType: McpAuthType;
    oauthProvider?: string;
    oauthScopesSupported?: string[];
    disabled: boolean;
    isDefault: boolean;
}

export interface McpServerPayload {
    id: string;
    description?: string;
    instructions?: string;
    serverType: "PRIVATE" | "PUBLIC";
    authType: McpAuthType;
    oauthProvider?: string;
    oauthScopesSupported?: string[];
    disabled: boolean;
}

export interface McpToolAnnotations {
    readOnly: boolean;
    openWorld: boolean;
    destructive: boolean;
    idempotent: boolean;
    returnDirect: boolean;
}

export interface McpTool {
    toolName: string;
    triggerId: string;
    title: string;
    description: string;
    annotations: McpToolAnnotations;
    namespace: string;
    flowId: string;
    flowRevision: number;
    disabled: boolean;
}

export const useMcpStore = defineStore("mcp", () => {
    const axios = useClient()
    const server = ref<McpServer | null>(null)

    const list = async (): Promise<{results: McpServer[], total: number}> => {
        const {data} = await axios.get(`${apiUrl()}/mcp/servers`)
        return data
    }

    const load = async (id: string): Promise<void> => {
        try {
            const {data} = await axios.get(`${apiUrl()}/mcp/servers/${id}`)
            server.value = data
        } catch {
            server.value = null
        }
    }

    const create = async (payload: McpServerPayload): Promise<McpServer> => {
        const {data} = await axios.post(`${apiUrl()}/mcp/servers`, payload)
        return data
    }

    const update = async (id: string, payload: McpServerPayload): Promise<McpServer> => {
        const {data} = await axios.put(`${apiUrl()}/mcp/servers/${id}`, payload)
        return data
    }

    const remove = async (id: string): Promise<void> => {
        await axios.delete(`${apiUrl()}/mcp/servers/${id}`)
    }

    const toggle = async (id: string): Promise<McpServer> => {
        const {data} = await axios.patch(`${apiUrl()}/mcp/servers/${id}/toggle`)
        return data
    }

    const listTools = async (id: string): Promise<McpTool[]> => {
        const {data} = await axios.get(`${apiUrl()}/mcp/servers/${id}/tools`)
        return data
    }

    return {server, list, load, create, update, remove, toggle, listTools}
})
