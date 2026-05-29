<template>
    <TopNavBar :title="t('mcp.servers')" :longDescription="t('mcp.page_description')">
        <template v-if="!instanceMode && (isOSS || canCreate)" #actions>
            <Action
                :label="t('mcp.create')"
                :to="{name: 'admin/mcp-servers/create', params: {tab: 'edit'}}"
            />
        </template>
    </TopNavBar>

    <el-row class="p-5">
        <div v-if="loading" class="mcp-list__loading">
            <el-icon class="is-loading" :size="32">
                <Loading />
            </el-icon>
        </div>

        <KsEmpty v-else-if="displayServers.length === 0" class="mcp-list__empty">
            {{ t("mcp.no_servers") }}
        </KsEmpty>

        <template v-else>
            <el-col
                v-for="server in displayServers"
                :key="server.tenantId ? `${server.tenantId}/${server.id}` : server.id"
                class="mcp-list__item"
            >
                <component
                    :is="canView ? 'router-link' : 'div'"
                    v-bind="canView ? {to: routeFor(server)} : {}"
                    class="mcp-list__row"
                    :class="{'mcp-list__row--disabled': server.disabled}"
                >
                    <div class="mcp-list__icon">
                        <ServerNetwork />
                    </div>

                    <span class="mcp-list__name">{{ server.id }}</span>

                    <span v-if="instanceMode" class="mcp-list__tenant">
                        {{ server.tenantId ?? t("mcp.main_tenant") }}
                    </span>

                    <div class="mcp-list__badges">
                        <el-tooltip :content="server.serverType === 'PRIVATE' ? t('mcp.private') : t('mcp.public')">
                            <Lock v-if="server.serverType === 'PRIVATE'" class="mcp-list__type-icon" />
                            <Web v-else class="mcp-list__type-icon" />
                        </el-tooltip>
                        <span class="mcp-list__auth-badge">
                            {{ server.authType.replace("_", " ") }}
                        </span>
                        <span
                            class="mcp-list__status"
                            :class="server.disabled ? 'mcp-list__status--disabled' : 'mcp-list__status--enabled'"
                        >
                            {{ server.disabled ? t("disabled") : t("enabled") }}
                        </span>
                    </div>
                </component>
            </el-col>
        </template>
    </el-row>
</template>

<script lang="ts" setup>
    import {computed, onMounted, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import {useMcpStore, type McpServer} from "../../stores/mcp"
    import useRouteContext from "../../composables/useRouteContext"
    import TopNavBar from "../layout/TopNavBar.vue"
    import Action from "../namespaces/components/buttons/Action.vue"
    import ServerNetwork from "vue-material-design-icons/ServerNetwork.vue"
    import Lock from "vue-material-design-icons/Lock.vue"
    import Web from "vue-material-design-icons/Web.vue"
    import Loading from "vue-material-design-icons/Loading.vue"
    import {useMiscStore} from "override/stores/misc"
    import {useAuthStore} from "override/stores/auth"
    import resource from "../../models/resource"
    import action from "../../models/action"

    interface DisplayServer {
        id: string;
        tenantId?: string | null;
        serverType: "PRIVATE" | "PUBLIC";
        authType: string;
        disabled: boolean;
    }

    const props = defineProps<{
        instanceServers?: DisplayServer[];
    }>()

    const {t} = useI18n({useScope: "global"})

    const routeInfo = computed(() => ({title: t("mcp.servers")}))
    useRouteContext(routeInfo)

    const mcpStore = useMcpStore()
    const tenantServers = ref<McpServer[]>([])
    const loading = ref(false)

    const instanceMode = computed(() => props.instanceServers !== undefined)

    const isOSS = computed(() => useMiscStore().configs?.edition === "OSS")
    const authStore = useAuthStore()
    const canCreate = computed(() => authStore.user?.hasAnyAction?.(resource.MCP_SERVER, action.CREATE))
    const canView = computed(() => isOSS.value || authStore.user?.hasAnyAction?.(resource.MCP_SERVER, action.VIEW))
    const displayServers = computed<DisplayServer[]>(() =>
        instanceMode.value ? (props.instanceServers ?? []) : tenantServers.value,
    )

    function routeFor(server: DisplayServer) {
        const params: Record<string, string> = {id: server.id, tab: "edit"}
        if (instanceMode.value) {
            params.tenant = server.tenantId ?? "main"
        }
        return {name: "admin/mcp-servers/update", params}
    }

    onMounted(async () => {
        if (instanceMode.value) return
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
    .mcp-list {
        &__loading {
            display: flex;
            justify-content: center;
            width: 100%;
            padding: 2rem;
        }

        &__empty {
            width: 100%;
        }

        &__item {
            margin: 0.25rem 0;
            border-radius: var(--ks-radius-lg);
            border: 1px solid var(--ks-border-default);
            box-shadow: 0px 2px 4px 0px var(--ks-shadow-element);
            background: var(--ks-bg-surface);
        }

        &__row {
            display: flex;
            width: 100%;
            align-items: center;
            gap: 0.75rem;
            padding: 0.625rem 0.875rem;
            border-radius: var(--ks-radius-lg);
            color: var(--ks-text-primary);
            text-decoration: none;
            transition: background 0.15s;

            &:hover {
                background: var(--ks-bg-body);
                color: var(--ks-text-link);
            }

            &--disabled {
                opacity: 0.6;
            }
        }

        &__icon {
            display: flex;
            align-items: center;
            color: var(--ks-text-link);
            font-size: 1.125rem;
        }

        &__name {
            font-weight: 600;
            font-size: 0.9375rem;
            flex-shrink: 0;
        }

        &__tenant {
            font-size: 0.8125rem;
            color: var(--ks-text-secondary);
            flex-shrink: 0;
        }

        &__badges {
            display: flex;
            align-items: center;
            gap: 0.5rem;
            margin-left: auto;
        }

        &__type-icon {
            color: var(--ks-text-secondary);
            font-size: 0.875rem;
        }

        &__auth-badge {
            font-size: 0.6875rem;
            font-weight: 600;
            letter-spacing: 0.05em;
            text-transform: uppercase;
            color: var(--ks-text-secondary);
            background: var(--ks-bg-body);
            border: 1px solid var(--ks-border-default);
            border-radius: 4px;
            padding: 1px 5px;
        }

        &__status {
            font-size: 0.6875rem;
            font-weight: 600;
            letter-spacing: 0.05em;
            text-transform: uppercase;
            border-radius: 4px;
            padding: 1px 5px;

            &--enabled {
                color: var(--ks-text-success);
                background: var(--ks-bg-success);
                border: 1px solid var(--ks-border-success);
            }

            &--disabled {
                color: var(--ks-text-secondary);
                background: var(--ks-bg-body);
                border: 1px solid var(--ks-border-default);
            }
        }

    }
</style>
