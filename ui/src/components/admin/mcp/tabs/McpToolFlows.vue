<template>
    <div class="mcp-tools">
        <Empty v-if="loaded && tools.length === 0" type="mcpToolFlows">
            <template v-if="canCreateFlow" #button>
                <KsButton type="primary" :icon="Plus" @click="createToolFlow">
                    {{ t("mcp.tools.create_tool_flow") }}
                </KsButton>
            </template>
        </Empty>
        <KsDataTable
            v-else
            :data="filteredTools"
            :total="filteredTools.length"
            :loading="loading"
            :rowKey="(row: McpTool) => `${row.namespace}/${row.flowId}/${row.triggerId}`"
            :noDataText="t('mcp.tools.no_tools')"
        >
            <template #navbar>
                <KSFilter
                    :prefix="'mcpTools'"
                    :configuration="toolsFilter"
                    :defaultScope="false"
                    :defaultTimeRange="false"
                    :buttons="{savedFilters: {shown: false}}"
                    :tableOptions="{
                        chart: {shown: false},
                        refresh: {shown: true, callback: load},
                    }"
                    :properties="{
                        shown: true,
                        columns: optionalColumns,
                        displayColumns,
                        storageKey,
                    }"
                    @update-properties="updateDisplayColumns"
                />
            </template>

            <KsTableColumn prop="state" :label="t('mcp.tools.state')" width="120">
                <template #default="scope">
                    <KsTag :type="(scope.row as McpTool).disabled ? 'info' : 'success'">
                        {{ (scope.row as McpTool).disabled ? t("disabled") : t("enabled") }}
                    </KsTag>
                </template>
            </KsTableColumn>

            <KsTableColumn prop="toolName" :label="t('mcp.tools.tool_name')">
                <template #default="scope">
                    <KsId :value="(scope.row as McpTool).toolName" :shrink="false" />
                </template>
            </KsTableColumn>

            <KsTableColumn
                v-for="col in visibleColumns"
                :key="col.prop"
                :prop="col.prop"
                :label="col.label"
            >
                <template #default="scope">
                    <template v-if="col.prop === 'triggerId'">
                        <code class="mcp-tools__mono">{{ (scope.row as McpTool).triggerId }}</code>
                    </template>
                    <template v-else-if="col.prop === 'title'">
                        {{ (scope.row as McpTool).title }}
                    </template>
                    <template v-else-if="col.prop === 'description'">
                        <span class="mcp-tools__description" :title="(scope.row as McpTool).description">
                            {{ (scope.row as McpTool).description }}
                        </span>
                    </template>
                    <template v-else-if="col.prop === 'annotations'">
                        <div class="mcp-tools__annotations">
                            <KsTag
                                v-for="ann in activeAnnotations((scope.row as McpTool).annotations)"
                                :key="ann"
                                type="info"
                            >
                                {{ t(`mcp.tools.${ann}`) }}
                            </KsTag>
                        </div>
                    </template>
                    <template v-else-if="col.prop === 'flow'">
                        <router-link
                            :to="flowRouteFor(scope.row as McpTool)"
                            class="mcp-tools__flow-link"
                            :title="t('mcp.tools.view_flow')"
                        >
                            <KsTag type="info">
                                <FolderOpenOutline />
                                {{ (scope.row as McpTool).namespace }}
                            </KsTag>
                            <span class="mcp-tools__flow-id">{{ (scope.row as McpTool).flowId }}</span>
                        </router-link>
                    </template>
                </template>
            </KsTableColumn>
        </KsDataTable>
    </div>
</template>

<script lang="ts" setup>
    import {computed, onMounted, ref, watch} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import {useI18n} from "vue-i18n"
    import {KsButton, KsDataTable, KsFilter as KSFilter, KsId, KsTableColumn, KsTag, decodeSearchParams} from "@kestra-io/design-system"
    import FolderOpenOutline from "vue-material-design-icons/FolderOpenOutline.vue"
    import Plus from "vue-material-design-icons/Plus.vue"
    import Empty from "../../../layout/empty/Empty.vue"
    import {useMcpStore, type McpTool, type McpToolAnnotations} from "../../../../stores/mcp"
    import {useMcpToolsFilter} from "../../../filter/configurations"
    import {type ColumnConfig, useTableColumns} from "../../../../composables/useTableColumns"
    import {storageKeys} from "../../../../utils/constants"
    import {useMiscStore} from "override/stores/misc"
    import {useAuthStore} from "override/stores/auth"
    import resource from "../../../../models/resource"
    import action from "../../../../models/action"

    const {t} = useI18n({useScope: "global"})
    const route = useRoute()
    const router = useRouter()
    const mcpStore = useMcpStore()
    const toolsFilter = useMcpToolsFilter()
    const authStore = useAuthStore()

    const isOSS = computed(() => useMiscStore().configs?.edition === "OSS")
    const canCreateFlow = computed(() => isOSS.value || authStore.user?.hasAnyAction?.(resource.FLOW, action.CREATE))

    const tools = ref<McpTool[]>([])
    const loading = ref(false)
    const loaded = ref(false)

    const serverId = () => route.params.id as string | undefined

    async function load() {
        const id = serverId()
        if (!id) return
        loading.value = true
        try {
            tools.value = await mcpStore.listTools(id)
        } finally {
            loading.value = false
            loaded.value = true
        }
    }

    onMounted(load)
    watch(() => route.params.id, load)

    /**
     * The search bar in `KsFilter` writes its value into the URL as a `QUERY` filter.
     * Filtering it server-side via the FLOW resource's QUERY field would match against the
     * entire flow object (source code included), so it pulls in flows that match on body
     * content rather than on the tool itself. We filter locally instead.
     */
    const searchQuery = computed(() => {
        const params = decodeSearchParams(route.query)
        const queryParam = params.find((p) => p.field === "q")
        const value = queryParam?.value
        if (!value) return ""
        return Array.isArray(value) ? value.join(" ").trim() : value.trim()
    })

    const filteredTools = computed<McpTool[]>(() => {
        if (!searchQuery.value) return tools.value
        const needle = searchQuery.value.toLowerCase()
        return tools.value.filter((tool) => {
            return [
                tool.toolName,
                tool.triggerId,
                tool.title,
                tool.description,
                tool.namespace,
                tool.flowId,
            ].some((field) => field && field.toLowerCase().includes(needle))
        })
    })

    const storageKey = storageKeys.DISPLAY_MCP_TOOLS_COLUMNS

    const optionalColumns = computed<ColumnConfig[]>(() => [
        {label: t("mcp.tools.trigger_id"), prop: "triggerId", default: false},
        {label: t("mcp.tools.title"), prop: "title", default: true},
        {label: t("mcp.tools.description"), prop: "description", default: false},
        {label: t("mcp.tools.annotations"), prop: "annotations", default: true},
        {label: t("mcp.tools.flow"), prop: "flow", default: true},
    ])

    const {visibleColumns: displayColumns, updateVisibleColumns: updateDisplayColumns} = useTableColumns({
        columns: optionalColumns.value,
        storageKey,
        initialVisibleColumns: optionalColumns.value.filter((c) => c.default).map((c) => c.prop),
    })

    const visibleColumns = computed<ColumnConfig[]>(() =>
        displayColumns.value
            .map((prop) => optionalColumns.value.find((c) => c.prop === prop))
            .filter(Boolean) as ColumnConfig[],
    )

    const ANNOTATION_KEYS: (keyof McpToolAnnotations)[] = [
        "readOnly", "openWorld", "destructive", "idempotent", "returnDirect",
    ]

    function activeAnnotations(a: McpToolAnnotations): string[] {
        return ANNOTATION_KEYS
            .filter((k) => a[k])
            .map((k) => k.replace(/([A-Z])/g, "_$1").toLowerCase())
    }

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

    function createToolFlow() {
        router.push({
            name: "flows/create",
            query: {
                blueprintId: "mcp-tool-trigger",
                blueprintSourceYaml: starterFlow(serverId() ?? "default"),
            },
            ...(route.params.tenant ? {params: {tenant: route.params.tenant}} : {}),
        })
    }

    function flowRouteFor(tool: McpTool) {
        return {
            name: "flows/update",
            params: {
                namespace: tool.namespace,
                id: tool.flowId,
                tab: "source",
                ...(route.params.tenant ? {tenant: route.params.tenant} : {}),
            },
        }
    }
</script>

<style lang="scss" scoped>
    .mcp-tools {
        &__mono {
            font-family: var(--ks-font-family-mono);
            font-size: 0.8125rem;
            color: var(--ks-text-primary);
            background: transparent;
            padding: 0;
        }

        &__description {
            display: -webkit-box;
            -webkit-line-clamp: 2;
            line-clamp: 2;
            -webkit-box-orient: vertical;
            overflow: hidden;
            color: var(--ks-text-secondary);
            font-size: 0.875rem;
        }

        &__annotations {
            display: flex;
            flex-wrap: wrap;
            gap: 0.25rem;
        }

        &__flow-link {
            display: inline-flex;
            align-items: center;
            gap: 0.5rem;
            color: var(--ks-text-primary);
            text-decoration: none;

            &:hover {
                color: var(--ks-text-link);
            }
        }

        &__flow-id {
            font-family: var(--ks-font-family-mono);
            font-size: 0.8125rem;
        }
    }
</style>
