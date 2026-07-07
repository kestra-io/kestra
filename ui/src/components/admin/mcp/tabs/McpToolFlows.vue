<template>
    <div class="mcp-tools">
        <Empty
            v-if="isEmpty"
            type="mcpToolFlows"
        >
            <template
                v-if="canCreateFlow"
                #button
            >
                <KsButton
                    type="primary"
                    :icon="Plus"
                    @click="createToolFlow"
                >
                    {{ t("mcp.tools.create_tool_flow") }}
                </KsButton>
            </template>
        </Empty>

        <KsDataTable
            v-else
            :data="filteredTools"
            :total="filteredTools.length"
            :loading="loading"
            :rowKey="rowKey"
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

            <KsTableColumn
                prop="toolName"
                :label="t('mcp.tools.tool_name')"
            >
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
                        <code class="mono">{{ (scope.row as McpTool).triggerId }}</code>
                    </template>
                    <template v-else-if="col.prop === 'title'">
                        {{ (scope.row as McpTool).title }}
                    </template>
                    <template v-else-if="col.prop === 'description'">
                        <span
                            class="description"
                            :title="(scope.row as McpTool).description"
                        >
                            {{ (scope.row as McpTool).description }}
                        </span>
                    </template>
                    <template v-else-if="col.prop === 'annotations'">
                        <div class="annotations">
                            <KsTag
                                v-for="ann in activeAnnotations((scope.row as McpTool).annotations)"
                                :key="ann"
                                class="annotation"
                            >
                                {{ t(`mcp.tools.${ann}`) }}
                            </KsTag>
                        </div>
                    </template>
                    <template v-else-if="col.prop === 'flow'">
                        <router-link
                            :to="flowRouteFor(scope.row as McpTool)"
                            class="flow-link"
                            :title="t('mcp.tools.view_flow')"
                        >
                            <span class="flow-id">{{ (scope.row as McpTool).flowId }}</span>
                        </router-link>
                    </template>
                    <template v-else-if="col.prop === 'namespace'">
                        <span class="namespace">
                            <FolderOpenOutline />
                            {{ (scope.row as McpTool).namespace }}
                        </span>
                    </template>
                </template>
            </KsTableColumn>

            <KsTableColumn
                prop="state"
                :label="t('mcp.tools.state')"
                width="120"
            >
                <template #default="scope">
                    <KsTag :type="stateType(scope.row as McpTool)">
                        {{ stateLabel(scope.row as McpTool) }}
                    </KsTag>
                </template>
            </KsTableColumn>
        </KsDataTable>
    </div>
</template>

<script lang="ts" setup>
    import {computed, onMounted, ref, watch} from "vue"
    import {useRoute, type RouteLocationRaw} from "vue-router"
    import {useI18n} from "vue-i18n"

    import {useMcpStore, type McpTool, type McpToolAnnotations} from "../../../../stores/mcp"

    import {useMcpToolsFilter} from "../../../filter/configurations"
    import {type ColumnConfig, useTableColumns} from "../../../../composables/useTableColumns"
    import {useToolFlowCreation} from "../useToolFlowCreation"

    import {KsButton, KsDataTable, KsFilter as KSFilter, KsId, KsTableColumn, KsTag, decodeSearchParams} from "@kestra-io/design-system"
    import FolderOpenOutline from "vue-material-design-icons/FolderOpenOutline.vue"
    import Plus from "vue-material-design-icons/Plus.vue"
    import Empty from "../../../layout/empty/Empty.vue"

    import {storageKeys} from "../../../../utils/constants"

    const ANNOTATION_KEYS: (keyof McpToolAnnotations)[] = [
        "readOnly", "openWorld", "destructive", "idempotent", "returnDirect",
    ]
    const storageKey = storageKeys.DISPLAY_MCP_TOOLS_COLUMNS

    const {t} = useI18n({useScope: "global"})
    const route = useRoute()
    const mcpStore = useMcpStore()
    const toolsFilter = useMcpToolsFilter()
    const {canCreateFlow, createToolFlow} = useToolFlowCreation()

    const tools = ref<McpTool[]>([])
    const loading = ref(false)
    const loaded = ref(false)

    const optionalColumns = computed<ColumnConfig[]>(() => [
        {
            label: t("mcp.tools.title"),
            prop: "title",
            default: true,
            description: t("filter.table_column.mcpTools.title"),
        },
        {
            label: t("mcp.tools.annotations"),
            prop: "annotations",
            default: true,
            description: t("filter.table_column.mcpTools.annotations"),
        },
        {
            label: t("mcp.tools.flow"),
            prop: "flow",
            default: true,
            description: t("filter.table_column.mcpTools.flow"),
        },
        {
            label: t("namespace"),
            prop: "namespace",
            default: true,
            description: t("filter.table_column.mcpTools.namespace"),
        },
        {
            label: t("mcp.tools.trigger_id"),
            prop: "triggerId",
            default: false,
            description: t("filter.table_column.mcpTools.triggerId"),
        },
        {
            label: t("mcp.tools.description"),
            prop: "description",
            default: false,
            description: t("filter.table_column.mcpTools.description"),
        },
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

    const serverId = computed(() => route.params.id as string | undefined)
    const isEmpty = computed(() => loaded.value && tools.value.length === 0)

    /**
     * The search bar in `KsFilter` writes its value into the URL as a `QUERY` filter.
     * Filtering it server-side via the FLOW resource's QUERY field would match against the
     * entire flow object (source code included), so it pulls in flows that match on body
     * content rather than on the tool itself. We filter locally instead.
     */
    const searchQuery = computed(() => {
        const value = decodeSearchParams(route.query).find((p) => p.field === "q")?.value
        if (!value) {
            return ""
        }
        if (Array.isArray(value)) {
            return value.join(" ").trim()
        }
        return value.trim()
    })

    const filteredTools = computed<McpTool[]>(() => {
        if (!searchQuery.value) {
            return tools.value
        }
        const needle = searchQuery.value.toLowerCase()
        return tools.value.filter((tool) =>
            [tool.toolName, tool.triggerId, tool.title, tool.description, tool.namespace, tool.flowId]
                .some((field) => field && field.toLowerCase().includes(needle)),
        )
    })

    function activeAnnotations(annotations: McpToolAnnotations): string[] {
        return ANNOTATION_KEYS
            .filter((key) => annotations[key])
            .map((key) => key.replace(/([A-Z])/g, "_$1").toLowerCase())
    }

    function rowKey(row: McpTool): string {
        return `${row.namespace}/${row.flowId}/${row.triggerId}`
    }

    function stateType(tool: McpTool): "success" | undefined {
        return tool.disabled ? undefined : "success"
    }

    function stateLabel(tool: McpTool): string {
        return tool.disabled ? t("disabled") : t("enabled")
    }

    function flowRouteFor(tool: McpTool): RouteLocationRaw {
        return {
            name: "flows/update",
            params: {
                namespace: tool.namespace,
                id: tool.flowId,
                tab: "edit",
                ...(route.params.tenant ? {tenant: route.params.tenant} : {}),
            },
        }
    }

    async function load() {
        const id = serverId.value
        if (!id) {
            return
        }
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
</script>

<style lang="scss" scoped>
    .mono {
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-primary);
        background: transparent;
        padding: 0;
    }

    .description {
        display: -webkit-box;
        -webkit-line-clamp: 2;
        line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
    }

    .annotations {
        display: flex;
        flex-wrap: wrap;
        gap: var(--ks-spacing-1);
    }

    .annotation {
        border: none;
        background: var(--ks-bg-tag);
    }

    .flow-link {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        color: var(--ks-text-primary);
        text-decoration: none;

        &:hover {
            color: var(--ks-text-link);
        }
    }

    .flow-id {
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-sm);
    }

    .namespace {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        color: var(--ks-text-primary);
    }
</style>