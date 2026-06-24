<template>
    <component
        :is="tag"
        v-bind="linkAttrs"
        class="server-card"
        :class="{'is-link': isLink}"
    >
        <div class="avatar">
            <ServerNetworkOutline :size="24" />
        </div>

        <div class="details">
            <h4 class="title">{{ id }}</h4>

            <div class="meta-row">
                <span class="meta">
                    <span class="label">{{ t("type") }}:</span>
                    <span>{{ typeLabel }}</span>
                </span>
                <span
                    v-if="tenant"
                    class="meta"
                >
                    <span class="label">{{ t("tenant.name") }}:</span>
                    <span>{{ tenant }}</span>
                </span>
            </div>

            <div class="meta-row">
                <span class="meta">
                    <span class="label">{{ t("mcp.auth_type") }}:</span>
                    <span>{{ authLabel }}</span>
                </span>
            </div>
        </div>

        <div class="status">
            <KsTag
                v-if="isDefault"
                size="small"
                class="status-tag managed"
            >
                {{ t("mcp.managed_by_kestra") }}
                <KsIcon>
                    <Lock />
                </KsIcon>
            </KsTag>

            <KsTag
                size="small"
                :type="statusType"
                class="status-tag"
            >
                {{ statusLabel }}
            </KsTag>

            <KsIconButton
                v-if="canDelete && !isDefault"
                :tooltip="t('delete')"
                placement="left"
                @click.stop.prevent="emit('delete')"
            >
                <Delete class="delete-icon" />
            </KsIconButton>
        </div>
    </component>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"

    import {type McpAuthType, type McpServer} from "../../stores/mcp"

    import ServerNetworkOutline from "vue-material-design-icons/ServerNetworkOutline.vue"
    import Lock from "vue-material-design-icons/Lock.vue"
    import Delete from "vue-material-design-icons/DeleteOutline.vue"

    import type {RouteLocationRaw} from "vue-router"

    const props = defineProps<{
        id: string;
        serverType: McpServer["serverType"];
        authType: McpAuthType;
        disabled: boolean;
        isDefault?: boolean;
        tenant?: string | null;
        to?: RouteLocationRaw | null;
        canDelete?: boolean;
    }>()

    const emit = defineEmits<{
        delete: [];
    }>()

    const {t} = useI18n({useScope: "global"})

    const AUTH_LABELS: Record<McpAuthType, string> = {
        BASIC: "mcp.basic_auth",
        API_TOKEN: "mcp.api_token",
        OAUTH: "mcp.oauth",
    }

    const isLink = computed(() => Boolean(props.to))
    const tag = computed(() => (isLink.value ? "router-link" : "article"))
    const linkAttrs = computed(() => (props.to ? {to: props.to} : {}))

    const typeLabel = computed(() => (props.serverType === "PRIVATE" ? t("mcp.private") : t("mcp.public")))
    const authLabel = computed(() => t(AUTH_LABELS[props.authType]))
    const statusLabel = computed(() => (props.disabled ? t("disabled") : t("enabled")))
    const statusType = computed(() => (props.disabled ? "danger" : "success"))
</script>

<style scoped lang="scss">
    .server-card {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-4);
        padding: var(--ks-spacing-5) 1.25rem;
        color: var(--ks-text-primary);
        text-decoration: none;

        & + & {
            border-top: var(--ks-border-block-secondary);
        }
    }

    .is-link {
        transition: background 0.15s;

        &:hover {
            background: var(--ks-bg-body);
        }
    }

    .avatar {
        flex: 0 0 auto;
        display: flex;
        align-items: center;
        justify-content: center;
        width: 44px;
        height: 44px;
        border: var(--ks-border-block-secondary);
        border-radius: var(--ks-radius-sm);
        color: var(--ks-icon-muted);

        :deep(.material-design-icon),
        :deep(.material-design-icon__svg) {
            width: 24px;
            height: 24px;
        }

        :deep(.material-design-icon) {
            display: inline-flex;
        }

        :deep(.material-design-icon__svg) {
            position: static;
        }
    }

    .details {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
    }

    .title {
        margin: 0;
        font-size: var(--ks-font-size-md);
        font-weight: var(--ks-font-weight-semibold);
        color: var(--ks-text-primary);
    }

    .meta-row {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: var(--ks-spacing-4);
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-primary);
    }

    .meta {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);

        .label {
            color: var(--ks-text-secondary);
        }
    }

    .status {
        flex: 0 0 auto;
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
    }

    .status-tag {
        display: inline-flex;
        align-items: center;
        font-weight: var(--ks-font-weight-semibold);
        border: none;
    }

    .managed {
        font-weight: var(--ks-font-weight-regular);
    }

    .delete-icon {
        color: var(--ks-icon-muted);
    }
</style>