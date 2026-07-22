<template>
    <div class="mcp-edit">
        <KsForm
            ref="formRef"
            :model="form"
            labelPosition="top"
            @submit.prevent="onSubmit"
        >
            <KsFormItem
                :label="t('mcp.server_id')"
                prop="id"
                required
                labelPosition="left"
                labelWidth="auto"
                class="id-row"
                :rules="idRules"
            >
                <KsInput
                    v-model="form.id"
                    :placeholder="t('mcp.id_placeholder')"
                    :disabled="idDisabled"
                    class="mono id-input"
                    @change="autoSubmit"
                >
                    <template
                        v-if="idDisabled"
                        #suffix
                    >
                        <Lock :size="16" />
                    </template>
                </KsInput>
            </KsFormItem>

            <KsFormItem :label="t('description')">
                <KsInput
                    v-model="form.description"
                    type="textarea"
                    :rows="2"
                    :placeholder="t('description')"
                    :disabled="readOnly"
                    @change="autoSubmit"
                />
            </KsFormItem>

            <KsFormItem :label="t('mcp.instructions')">
                <KsInput
                    v-model="form.instructions"
                    type="textarea"
                    :rows="3"
                    :placeholder="t('mcp.instructions')"
                    class="mono"
                    :disabled="readOnly"
                    @change="autoSubmit"
                />
            </KsFormItem>

            <KsFormItem
                :label="t('mcp.private_server')"
                labelPosition="left"
                class="spread-row"
            >
                <KsSwitch
                    v-model="privateServer"
                    :disabled="readOnly"
                    @change="autoSubmit"
                />
            </KsFormItem>

            <KsAlert
                v-if="!isPrivate"
                type="warning"
                :closable="false"
                class="type-hint"
            >
                {{ t("mcp.public_hint") }}
            </KsAlert>

            <KsFormItem v-if="isPrivate">
                <KsRadioCardGroup
                    v-model="form.authType"
                    :options="authOptions"
                    @change="autoSubmit"
                />
            </KsFormItem>

            <KsFormItem
                v-if="isOAuth"
                :label="t('mcp.oauth_provider')"
                prop="oauthProvider"
                :rules="oauthProviderRules"
            >
                <KsSelect
                    v-model="form.oauthProvider"
                    :placeholder="t('mcp.oauth_provider_placeholder')"
                    :disabled="readOnly"
                    class="full-width"
                    @change="autoSubmit"
                >
                    <KsOption
                        v-for="provider in oauthProviders"
                        :key="provider"
                        :label="provider"
                        :value="provider"
                    />
                </KsSelect>
            </KsFormItem>

            <KsFormItem
                v-if="isOAuth"
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
                    class="full-width"
                    @change="autoSubmit"
                />
                <div class="field-hint">
                    {{ t("mcp.scopes_supported_hint") }}
                </div>
            </KsFormItem>

            <KsFormItem
                :label="t('enabled')"
                labelPosition="left"
                class="spread-row"
            >
                <KsSwitch
                    v-model="enabled"
                    :disabled="readOnly"
                    @change="autoSubmit"
                />
            </KsFormItem>
        </KsForm>
    </div>
</template>

<script lang="ts" setup>
    import {computed, onMounted, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"

    import {useMcpStore, type McpServerPayload} from "../../../../stores/mcp"
    import {useMiscStore} from "override/stores/misc"
    import {useAuthStore} from "override/stores/auth"

    import {useToast} from "../../../../utils/toast"

    import Lock from "vue-material-design-icons/Lock.vue"
    import LockOutline from "vue-material-design-icons/LockOutline.vue"

    import resource from "../../../../models/resource"
    import action from "../../../../models/action"
    import type {FormInstance} from "@kestra-io/design-system"

    const {t} = useI18n({useScope: "global"})
    const route = useRoute()
    const router = useRouter()
    const toast = useToast()
    const mcpStore = useMcpStore()
    const authStore = useAuthStore()
    const miscStore = useMiscStore()

    const DEFAULT_OAUTH_SCOPES = ["openid", "profile", "email"]

    const AUTH_OPTIONS = [
        {value: "BASIC", labelKey: "mcp.basic_auth", hintKey: "mcp.username_password", ee: false},
        {value: "API_TOKEN", labelKey: "mcp.api_token", hintKey: "mcp.bearer_token", ee: true},
        {value: "OAUTH", labelKey: "mcp.oauth", hintKey: "mcp.oauth_hint", ee: true},
    ] as const

    type AuthOption = (typeof AUTH_OPTIONS)[number]

    type McpForm = Required<McpServerPayload>

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
    const submitting = ref(false)

    const isOss = computed(() => miscStore.configs?.edition === "OSS")
    const oauthProviders = computed<string[]>(() => authStore.auths?.oauths ?? [])
    const noOauthProviders = computed(() => oauthProviders.value.length === 0)

    const isUpdate = computed(() => !!route.params.id)
    const isPrivate = computed(() => form.value.serverType === "PRIVATE")
    const isOAuth = computed(() => isPrivate.value && form.value.authType === "OAUTH")

    const privateServer = computed({
        get: () => form.value.serverType === "PRIVATE",
        set: (value: boolean) => {
            form.value.serverType = value ? "PRIVATE" : "PUBLIC"
        },
    })

    const canSave = computed(() => {
        if (isUpdate.value) {
            return authStore.user?.isAllowedGlobal?.(resource.MCP_SERVER, action.UPDATE) ?? true
        }
        return authStore.user?.isAllowedGlobal?.(resource.MCP_SERVER, action.CREATE) ?? true
    })
    const readOnly = computed(() => !canSave.value)
    const idDisabled = computed(() => isUpdate.value || readOnly.value)

    const idRules = computed(() => [
        {required: true, message: `${t("id")} ${t("required")}`, trigger: "blur"},
        {pattern: /^[a-z0-9][a-z0-9_-]*$/, message: t("mcp.id_invalid"), trigger: "blur"},
    ])

    const oauthProviderRules = computed(() => [
        {required: true, message: t("mcp.oauth_provider_required"), trigger: "change"},
    ])

    const enabled = computed({
        get: () => !form.value.disabled,
        set: (value: boolean) => {
            form.value.disabled = !value
        },
    })

    const isOptionDisabled = (opt: AuthOption): boolean => {
        if (readOnly.value) {
            return true
        }
        if (opt.ee && isOss.value) {
            return true
        }
        if (opt.value === "OAUTH" && noOauthProviders.value) {
            return true
        }
        return false
    }

    const authHint = (opt: AuthOption): string => {
        if (opt.value === "OAUTH" && noOauthProviders.value) {
            return t("mcp.no_oauth_providers")
        }
        return t(opt.hintKey)
    }

    const authOptions = computed(() =>
        AUTH_OPTIONS.map((opt) => ({
            value: opt.value,
            label: t(opt.labelKey),
            hint: authHint(opt),
            disabled: isOptionDisabled(opt),
            icon: opt.ee && isOss.value ? LockOutline : undefined,
        })),
    )

    const buildPayload = (): McpServerPayload => {
        const isOauth = form.value.authType === "OAUTH"

        let oauthProvider: string | undefined
        let oauthScopesSupported: string[] | undefined
        if (isOauth) {
            oauthProvider = form.value.oauthProvider || undefined
            oauthScopesSupported = form.value.oauthScopesSupported.length > 0
                ? form.value.oauthScopesSupported
                : undefined
        }

        return {
            id: form.value.id,
            description: form.value.description || undefined,
            instructions: form.value.instructions || undefined,
            serverType: form.value.serverType,
            authType: form.value.authType,
            oauthProvider,
            oauthScopesSupported,
            disabled: form.value.disabled,
        }
    }

    const create = async (): Promise<void> => {
        if (!formRef.value || submitting.value) {
            return
        }

        await formRef.value.validate(async (valid) => {
            if (!valid) {
                return
            }

            submitting.value = true
            try {
                const created = await mcpStore.create(buildPayload())
                toast.saved(created.id)
                router.push({
                    name: "admin/mcp-servers/update",
                    params: {id: created.id, tab: "edit"},
                })
            } catch (e) {
                submitting.value = false
                console.error("Failed to create MCP server", e)
            }
        }).catch(() => {})
    }

    const autoSubmit = (): void => {
        if (isUpdate.value) {
            autoSave()
            return
        }

        if (readOnly.value || !form.value.id) {
            return
        }

        create()
    }

    const autoSave = (): void => {
        if (!isUpdate.value || readOnly.value || !formRef.value) {
            return
        }

        formRef.value.validate(async (valid) => {
            if (!valid) {
                return
            }

            try {
                await mcpStore.update(form.value.id, buildPayload())
                toast.saved(form.value.id)
            } catch (e) {
                console.error("Failed to save MCP server", e)
            }
        }).catch(() => {})
    }

    const onSubmit = (): void => {
        if (isUpdate.value) {
            autoSave()
        } else {
            create()
        }
    }

    onMounted(() => {
        if (!authStore.auths) {
            authStore.loadAuths({})
        }
    })

    watch(
        () => mcpStore.server,
        (server) => {
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
        },
        {immediate: true},
    )
</script>

<style lang="scss" scoped>
    .mcp-edit {
        max-width: 653px;
        border: 1px solid var(--ks-border-default);
        border-radius: 8px;
        box-shadow: 0px 2px 8px 0px var(--ks-shadow-surface);
        background: var(--ks-bg-surface);
        padding: var(--ks-spacing-4);
        margin-block-start: var(--ks-spacing-7);
        margin-inline: auto;
    }

    .mono :deep(input),
    .mono :deep(textarea) {
        font-family: var(--ks-font-family-mono);
    }

    .mcp-edit :deep(textarea) {
        resize: none;
    }

    .mcp-edit :deep(textarea)::-webkit-scrollbar {
        width: 0.5rem;
    }

    .mcp-edit :deep(textarea)::-webkit-scrollbar-thumb {
        background-color: var(--ks-scrollbar-content);
        background-clip: padding-box;
        border: 2px solid transparent;
        border-radius: 999px;
    }

    :deep(.kel-form-item__label) {
        font-weight: var(--ks-font-weight-semibold);
    }

    .mcp-edit :deep(.kel-form-item:not(:first-child)) {
        border-top: 1px solid var(--ks-border-subtle);
        padding-top: var(--ks-spacing-4);
    }

    .id-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
    }

    .id-row :deep(.kel-form-item__content) {
        flex: 0 0 auto;
    }

    .spread-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
    }

    .spread-row :deep(.kel-form-item__content) {
        flex: 0 0 auto;
    }

    .spread-row:last-child {
        margin-bottom: 0;
    }

    .id-input {
        width: 170px;
        min-height: 30px;
    }

    .field-hint {
        margin-top: var(--ks-spacing-1);
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-secondary);
    }

    .type-hint {
        margin-bottom: var(--ks-spacing-4);
    }

    .full-width {
        width: 100%;
    }
</style>