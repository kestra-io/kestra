import {computed} from "vue"
import {useRoute, useRouter} from "vue-router"
import {useMiscStore} from "override/stores/misc"
import {useAuthStore} from "override/stores/auth"
import resource from "../../../models/resource"
import action from "../../../models/action"

function starterFlow(mcpServer: string): string {
    return `id: hello_world
namespace: company.team

inputs:
  - id: user
    type: STRING
    defaults: John Doe
    description: "The name of the user to greet."

tasks:
  - id: greet
    type: io.kestra.plugin.core.output.OutputValues
    values:
      greeting: "Hello, {{ inputs.user }}!"

outputs:
  - id: greeting
    type: STRING
    value: "{{ outputs.greet.values.greeting }}"

triggers:
  - id: mcp
    type: io.kestra.plugin.core.trigger.McpToolTrigger
    toolName: hello_world
    title: Hello World greeting tool
    toolDescription: Returns a personalised greeting. Call this when the user asks for a greeting.
    mcpServer: ${mcpServer}`
}

export function useToolFlowCreation() {
    const route = useRoute()
    const router = useRouter()
    const miscStore = useMiscStore()
    const authStore = useAuthStore()

    const isOSS = computed(() => miscStore.configs?.edition === "OSS")
    const canCreateFlow = computed(() =>
        isOSS.value || authStore.user?.hasAnyAction?.(resource.FLOW, action.CREATE),
    )

    function createToolFlow() {
        const serverId = (route.params.id as string | undefined) ?? "default"
        router.push({
            name: "flows/create",
            query: {
                blueprintId: "mcp-tool-trigger",
                blueprintSourceYaml: starterFlow(serverId),
            },
            ...(route.params.tenant ? {params: {tenant: route.params.tenant}} : {}),
        })
    }

    return {canCreateFlow, createToolFlow}
}
