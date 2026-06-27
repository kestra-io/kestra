<template>
    <TopNavBar
        :title="t('mcp.servers')"
        :longDescription="t('mcp.page_description')"
    >
        <template
            v-if="showCreate"
            #actions
        >
            <Action
                :label="t('mcp.create')"
                :to="{name: 'admin/mcp-servers/create', params: {tab: 'edit'}}"
            />
        </template>
    </TopNavBar>

    <section class="container">
        <div
            v-if="loading"
            class="loading"
        >
            <KsIcon
                class="is-loading"
                :size="32"
            >
                <Loading />
            </KsIcon>
        </div>

        <KsNoData
            v-else-if="displayServers.length === 0"
            class="empty"
            :description="t('mcp.no_servers')"
        />

        <div
            v-else
            class="server-list"
        >
            <h2 class="list-title">{{ t("mcp.your_servers") }}</h2>

            <section class="list-card">
                <header class="list-header">
                    {{ t("mcp.servers_count", {count: displayServers.length}) }}
                </header>

                <McpServerCard
                    v-for="server in displayServers"
                    :id="server.id"
                    :key="serverKey(server)"
                    :serverType="server.serverType"
                    :authType="server.authType"
                    :disabled="server.disabled"
                    :isDefault="server.isDefault"
                    :tenant="tenantLabel(server)"
                    :to="serverLink(server)"
                    :canDelete="canDelete"
                    @delete="confirmDelete(server)"
                />
            </section>
        </div>
    </section>
</template>

<script lang="ts" setup>
    import {computed, onMounted, ref} from "vue"
    import {useI18n} from "vue-i18n"

    import {useMcpStore, type McpServer} from "../../stores/mcp"
    import {useMiscStore} from "override/stores/misc"
    import {useAuthStore} from "override/stores/auth"

    import useRouteContext from "../../composables/useRouteContext"

    import TopNavBar from "../layout/TopNavBar.vue"
    import Action from "../namespaces/components/buttons/Action.vue"
    import McpServerCard from "./McpServerCard.vue"
    import Loading from "vue-material-design-icons/Loading.vue"

    import {useToast} from "../../utils/toast"
    import resource from "../../models/resource"
    import action from "../../models/action"
    import type {RouteLocationRaw} from "vue-router"

    type DisplayServer = Pick<McpServer, "id" | "serverType" | "authType" | "disabled"> & {
        tenantId?: string | null;
        isDefault?: boolean;
    };

    const props = defineProps<{
        instanceServers?: DisplayServer[];
    }>()

    const {t} = useI18n({useScope: "global"})
    const toast = useToast()
    const mcpStore = useMcpStore()
    const miscStore = useMiscStore()
    const authStore = useAuthStore()

    const tenantServers = ref<McpServer[]>([])
    const loading = ref(false)

    const instanceMode = computed(() => props.instanceServers !== undefined)
    const isOSS = computed(() => miscStore.configs?.edition === "OSS")

    const canCreate = computed(() =>
        authStore.user?.isAllowedGlobal?.(resource.MCP_SERVER, action.CREATE),
    )

    const canView = computed(() =>
        isOSS.value
        || authStore.user?.isAllowedGlobal?.(resource.MCP_SERVER, action.VIEW),
    )

    const canDelete = computed(() =>
        !instanceMode.value
        && (authStore.user?.isAllowedGlobal?.(resource.MCP_SERVER, action.DELETE) ?? true),
    )

    const showCreate = computed(() =>
        !instanceMode.value
        && (isOSS.value || Boolean(canCreate.value)),
    )

    const displayServers = computed<DisplayServer[]>(() =>
        instanceMode.value ? (props.instanceServers ?? []) : tenantServers.value,
    )

    const routeInfo = computed(() => ({title: t("mcp.servers")}))
    useRouteContext(routeInfo)

    const serverKey = (server: DisplayServer): string =>
        server.tenantId ? `${server.tenantId}/${server.id}` : server.id

    const tenantLabel = (server: DisplayServer): string | undefined => {
        if (!instanceMode.value) {
            return undefined
        }
        return server.tenantId ?? t("mcp.main_tenant")
    }

    const routeTo = (server: DisplayServer): RouteLocationRaw => {
        const params: Record<string, string> = {id: server.id, tab: "edit"}

        if (instanceMode.value) {
            return {
                name: "admin/instance/mcp-servers/update",
                params: {...params, tenant: server.tenantId ?? "main"},
            }
        }

        return {name: "admin/mcp-servers/update", params}
    }

    const serverLink = (server: DisplayServer): RouteLocationRaw | undefined =>
        canView.value ? routeTo(server) : undefined

    const confirmDelete = (server: DisplayServer): void => {
        toast.confirm(t("mcp.delete_confirm"), async () => {
            try {
                await mcpStore.remove(server.id)
                toast.deleted(server.id)
                tenantServers.value = tenantServers.value.filter((s) => s.id !== server.id)
            } catch (e) {
                console.error("Failed to delete MCP server", e)
            }
        })
    }

    onMounted(async () => {
        if (instanceMode.value) {
            return
        }

        loading.value = true
        try {
            const result = await mcpStore.list()
            tenantServers.value = result?.results ?? []
        } finally {
            loading.value = false
        }
    })
</script>

<style lang="scss" scoped>
    .loading {
        display: flex;
        justify-content: center;
        width: 100%;
        padding: var(--ks-spacing-6);
    }

    .empty {
        width: 100%;
    }

    .server-list {
        width: 100%;
        max-width: 600px;
        margin: var(--ks-spacing-6) auto 0;
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-3);
    }

    .list-title {
        margin: 0;
        font-size: var(--ks-font-size-lg);
        font-weight: var(--ks-font-weight-semibold);
        color: var(--ks-text-primary);
    }

    .list-card {
        background: var(--ks-bg-surface);
        border: var(--ks-border-block-primary);
        border-radius: var(--ks-radius-lg);
        box-shadow: 0px 2px 8px 0px var(--ks-shadow-surface);
        overflow: hidden;
    }

    .list-header {
        padding: var(--ks-spacing-4) 1.25rem;
        border-bottom: var(--ks-border-block-secondary);
        font-size: var(--ks-font-size-sm);
        font-weight: var(--ks-font-weight-regular);
        color: var(--ks-text-secondary);
    }
</style>