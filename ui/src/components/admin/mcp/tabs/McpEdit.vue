<template>
    <div class="mcp-edit">
        <el-form
            ref="formRef"
            :model="form"
            labelPosition="top"
            @submit.prevent="save"
        >
            <el-form-item
                :label="t('id')"
                prop="id"
                :rules="[
                    {required: true, message: t('id') + ' ' + t('required'), trigger: 'blur'},
                    {pattern: /^[a-z0-9][a-z0-9_-]*$/, message: t('mcp.id_invalid'), trigger: 'blur'},
                ]"
            >
                <el-input
                    v-model="form.id"
                    :placeholder="t('mcp.id_placeholder')"
                    :disabled="isUpdate || readOnly"
                    class="mcp-edit__name-input"
                />
            </el-form-item>

            <el-form-item :label="t('description')">
                <el-input
                    v-model="form.description"
                    type="textarea"
                    :rows="2"
                    :placeholder="t('description')"
                    :disabled="readOnly"
                />
            </el-form-item>

            <el-form-item :label="t('mcp.instructions')">
                <el-input
                    v-model="form.instructions"
                    type="textarea"
                    :rows="3"
                    :placeholder="t('mcp.instructions')"
                    class="mcp-edit__instructions-input"
                    :disabled="readOnly"
                />
            </el-form-item>

            <el-form-item :label="t('mcp.server_type')">
                <div class="mcp-edit__type-buttons">
                    <button
                        type="button"
                        class="mcp-edit__type-btn"
                        :class="{'mcp-edit__type-btn--active': form.serverType === 'PRIVATE'}"
                        :disabled="readOnly"
                        @click="form.serverType = 'PRIVATE'"
                    >
                        <Lock class="me-1" />
                        {{ t("mcp.private") }}
                    </button>
                    <button
                        type="button"
                        class="mcp-edit__type-btn"
                        :class="{'mcp-edit__type-btn--active': form.serverType === 'PUBLIC'}"
                        :disabled="readOnly"
                        @click="form.serverType = 'PUBLIC'"
                    >
                        <Web class="me-1" />
                        {{ t("mcp.public") }}
                    </button>
                </div>
            </el-form-item>

            <el-form-item v-if="form.serverType === 'PRIVATE'" :label="t('mcp.auth_type')">
                <div class="mcp-edit__auth-list">
                    <label
                        v-for="opt in AUTH_OPTIONS"
                        :key="opt.value"
                        class="mcp-edit__auth-option"
                        :class="{
                            'mcp-edit__auth-option--selected': form.authType === opt.value,
                            'mcp-edit__auth-option--disabled': isOptionDisabled(opt),
                        }"
                    >
                        <input
                            type="radio"
                            :value="opt.value"
                            v-model="form.authType"
                            class="me-2"
                            :disabled="isOptionDisabled(opt)"
                        >
                        <span class="mcp-edit__auth-name">{{ t(opt.labelKey) }}</span>
                        <LockOutline v-if="opt.ee && isOss" class="ms-2" :size="14" />
                        <span class="mcp-edit__auth-hint ms-auto">
                            {{ opt.value === "OAUTH" && noOauthProviders ? t("mcp.no_oauth_providers") : t(opt.hintKey) }}
                        </span>
                    </label>
                </div>
            </el-form-item>

            <el-form-item
                v-if="form.serverType === 'PRIVATE' && form.authType === 'OAUTH'"
                :label="t('mcp.oauth_provider')"
                prop="oauthProvider"
                :rules="[{required: true, message: t('mcp.oauth_provider_required'), trigger: 'change'}]"
            >
                <KsSelect
                    v-model="form.oauthProvider"
                    :placeholder="t('mcp.oauth_provider_placeholder')"
                    :disabled="readOnly"
                    class="mcp-edit__provider-select"
                >
                    <KsOption
                        v-for="provider in oauthProviders"
                        :key="provider"
                        :label="provider"
                        :value="provider"
                    />
                </KsSelect>
            </el-form-item>

            <el-form-item
                v-if="form.serverType === 'PRIVATE' && form.authType === 'OAUTH'"
                :label="t('mcp.scopes_supported')"
            >
                <KsSelect
                    v-model="form.oauthScopesSupported"
                    multiple
                    filterable
                    allowCreate
                    defaultFirstOption
                    :placeholder="t('mcp.scopes_supported_placeholder')"
                    :disabled="readOnly"
                    class="mcp-edit__provider-select"
                />
                <div class="mcp-edit__field-hint">
                    {{ t("mcp.scopes_supported_hint") }}
                </div>
            </el-form-item>

            <el-form-item :label="t('enabled')">
                <el-switch
                    :modelValue="!form.disabled"
                    :disabled="readOnly"
                    @update:model-value="(val: boolean) => (form.disabled = !val)"
                />
            </el-form-item>

            <div class="mcp-edit__actions">
                <KsButton v-if="canSave" type="primary" @click="save">
                    {{ isUpdate ? t("mcp.save") : t("mcp.create") }}
                </KsButton>
                <KsButton
                    v-if="isUpdate && !mcpStore.server?.isDefault && canDelete"
                    type="danger"
                    plain
                    @click="confirmDelete"
                >
                    {{ t("delete") }}
                </KsButton>
            </div>
        </el-form>
    </div>
</template>

<script lang="ts" setup>
    import {computed, onMounted, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import type {FormInstance} from "element-plus"
    import {useMcpStore} from "../../../../stores/mcp"
    import {useMiscStore} from "override/stores/misc"
    import {useAuthStore} from "override/stores/auth"
    import resource from "../../../../models/resource"
    import action from "../../../../models/action"
    import LockOutline from "vue-material-design-icons/LockOutline.vue"
    import Lock from "vue-material-design-icons/Lock.vue"
    import Web from "vue-material-design-icons/Web.vue"

    const {t} = useI18n({useScope: "global"})
    const route = useRoute()
    const router = useRouter()
    const mcpStore = useMcpStore()
    const authStore = useAuthStore()
    const isOss = computed(() => useMiscStore().configs?.edition === "OSS")
    const oauthProviders = computed<string[]>(() => authStore.auths?.oauths ?? [])
    const noOauthProviders = computed(() => oauthProviders.value.length === 0)

    type AuthOption = {value: "BASIC" | "API_TOKEN" | "OAUTH"; labelKey: string; hintKey: string; ee: boolean}
    function isOptionDisabled(opt: AuthOption) {
        if (readOnly.value) return true
        if (opt.ee && isOss.value) return true
        if (opt.value === "OAUTH" && noOauthProviders.value) return true
        return false
    }

    const isUpdate = computed(() => !!route.params.id)

    const canSave = computed(() =>
        isUpdate.value
            ? authStore.user?.isAllowedGlobal?.(resource.MCP_SERVER, action.UPDATE) ?? true
            : authStore.user?.isAllowedGlobal?.(resource.MCP_SERVER, action.CREATE) ?? true,
    )
    const canDelete = computed(() => authStore.user?.isAllowedGlobal?.(resource.MCP_SERVER, action.DELETE) ?? true)
    const readOnly = computed(() => !canSave.value)

    interface McpForm {
        id: string;
        description: string;
        instructions: string;
        serverType: "PRIVATE" | "PUBLIC";
        authType: "BASIC" | "API_TOKEN" | "OAUTH";
        oauthProvider: string;
        oauthScopesSupported: string[];
        disabled: boolean;
    }

    const DEFAULT_OAUTH_SCOPES = ["openid", "profile", "email"]

    const AUTH_OPTIONS = [
        {value: "BASIC" as const, labelKey: "mcp.basic_auth", hintKey: "mcp.username_password", ee: false},
        {value: "API_TOKEN" as const, labelKey: "mcp.api_token", hintKey: "mcp.bearer_token", ee: true},
        {value: "OAUTH" as const, labelKey: "mcp.oauth", hintKey: "mcp.oauth_hint", ee: true},
    ]

    const defaultForm = (): McpForm => ({
        id: "",
        description: "",
        instructions: "",
        serverType: "PRIVATE",
        authType: "BASIC",
        oauthProvider: "",
        oauthScopesSupported: [...DEFAULT_OAUTH_SCOPES],
        disabled: false,
    })

    const formRef = ref<FormInstance>()
    const form = ref<McpForm>(defaultForm())

    watch(() => mcpStore.server, (server) => {
        if (server) {
            form.value = {
                id: server.id,
                description: server.description ?? "",
                instructions: server.instructions ?? "",
                serverType: server.serverType,
                authType: server.authType,
                oauthProvider: server.oauthProvider ?? "",
                oauthScopesSupported: server.oauthScopesSupported ?? [],
                disabled: server.disabled,
            }
        } else if (!isUpdate.value) {
            form.value = defaultForm()
        }
    }, {immediate: true})

    onMounted(() => {
        if (!authStore.auths) {
            authStore.loadAuths({})
        }
    })

    const save = async (): Promise<void> => {
        if (!formRef.value) return
        await formRef.value.validate(async (valid) => {
            if (!valid) return
            try {
                const payload = {
                    id: form.value.id,
                    description: form.value.description || undefined,
                    instructions: form.value.instructions || undefined,
                    serverType: form.value.serverType,
                    authType: form.value.authType,
                    oauthProvider: form.value.authType === "OAUTH" ? form.value.oauthProvider || undefined : undefined,
                    oauthScopesSupported: form.value.authType === "OAUTH" && form.value.oauthScopesSupported.length > 0
                        ? form.value.oauthScopesSupported
                        : undefined,
                    disabled: form.value.disabled,
                }
                if (isUpdate.value) {
                    await mcpStore.update(form.value.id, payload)
                    router.push({name: "admin/mcp-servers"})
                } else {
                    const created = await mcpStore.create(payload)
                    router.push({
                        name: "admin/mcp-servers/update",
                        params: {id: created.id, tab: "edit"},
                    })
                }
            } catch (e) {
                console.error("Failed to save MCP server", e)
            }
        })
    }

    const confirmDelete = async (): Promise<void> => {
        if (!confirm(t("mcp.delete_confirm"))) return
        try {
            await mcpStore.remove(mcpStore.server!.id)
            router.push({name: "admin/mcp-servers"})
        } catch (e) {
            console.error("Failed to delete MCP server", e)
        }
    }
</script>

<style lang="scss" scoped>
    .mcp-edit {
        &__name-input {
            :deep(input) {
                font-family: monospace;
            }
        }

        &__instructions-input {
            :deep(textarea) {
                font-family: monospace;
            }
        }

        &__type-buttons {
            display: flex;
            width: 100%;
            gap: 0.5rem;
        }

        &__type-btn {
            display: inline-flex;
            flex: 1;
            align-items: center;
            justify-content: center;
            padding: 0.5rem 1rem;
            border: 1px solid var(--ks-border-default);
            border-radius: var(--ks-radius-base);
            background: var(--ks-bg-surface);
            color: var(--ks-text-primary);
            cursor: pointer;
            transition: all 0.15s;

            &--active {
                border-color: var(--ks-border-focus);
                background: var(--ks-bg-tag-active);
                color: var(--ks-text-link);
            }

            &:hover:not(.mcp-edit__type-btn--active) {
                border-color: var(--ks-border-strong);
            }

            &:disabled {
                opacity: 0.45;
                cursor: not-allowed;
            }
        }

        &__auth-list {
            display: flex;
            flex-direction: column;
            gap: 0.5rem;
            width: 100%;
        }

        &__auth-option {
            display: flex;
            align-items: center;
            padding: 0.625rem 0.75rem;
            border: 1px solid var(--ks-border-default);
            border-radius: var(--ks-radius-base);
            background: var(--ks-bg-surface);
            color: var(--ks-text-primary);
            cursor: pointer;
            transition: all 0.15s;

            &--selected {
                border-color: var(--ks-border-focus);
                background: var(--ks-bg-tag-active);
                color: var(--ks-text-link);
            }

            &--disabled {
                opacity: 0.45;
                cursor: not-allowed;
            }

            &:hover:not(.mcp-edit__auth-option--selected):not(.mcp-edit__auth-option--disabled) {
                border-color: var(--ks-border-strong);
            }
        }

        &__auth-name {
            font-weight: 500;
        }

        &__auth-hint {
            font-size: 0.8125rem;
            color: var(--ks-text-secondary);
        }

        &__provider-select {
            width: 100%;
        }

        &__field-hint {
            font-size: 0.8125rem;
            color: var(--ks-text-secondary);
            margin-top: 0.25rem;
        }

        &__actions {
            display: flex;
            gap: 0.75rem;
            padding-top: 0.5rem;
        }
    }
</style>
