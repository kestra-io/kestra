import axios from "axios"
import {defineStore} from "pinia"
import {ref} from "vue"
import {apiUrl} from "override/utils/route"

export interface McpServer {
    id: string;
    description?: string;
    instructions?: string;
    serverType: "PRIVATE" | "PUBLIC";
    authType: "BASIC" | "API_TOKEN";
    disabled: boolean;
    isDefault: boolean;
}

export interface McpServerPayload {
    id: string;
    description?: string;
    instructions?: string;
    serverType: "PRIVATE" | "PUBLIC";
    authType: "BASIC" | "API_TOKEN";
    disabled: boolean;
}

export const useMcpStore = defineStore("mcp", () => {
    const server = ref<McpServer | null>(null)

    const list = async (): Promise<{results: McpServer[], total: number}> => {
        const {data} = await axios.get(`${apiUrl()}/mcp/servers`, {withCredentials: true})
        return data
    }

    const load = async (id: string): Promise<void> => {
        try {
            const {data} = await axios.get(`${apiUrl()}/mcp/servers/${id}`, {withCredentials: true})
            server.value = data
        } catch {
            server.value = null
        }
    }

    const create = async (payload: McpServerPayload): Promise<McpServer> => {
        const {data} = await axios.post(`${apiUrl()}/mcp/servers`, payload, {withCredentials: true})
        return data
    }

    const update = async (id: string, payload: McpServerPayload): Promise<McpServer> => {
        const {data} = await axios.put(`${apiUrl()}/mcp/servers/${id}`, payload, {withCredentials: true})
        return data
    }

    const remove = async (id: string): Promise<void> => {
        await axios.delete(`${apiUrl()}/mcp/servers/${id}`, {withCredentials: true})
    }

    const toggle = async (id: string): Promise<McpServer> => {
        const {data} = await axios.patch(`${apiUrl()}/mcp/servers/${id}/toggle`, undefined, {withCredentials: true})
        return data
    }

    return {server, list, load, create, update, remove, toggle}
})
