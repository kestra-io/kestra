<template>
    <div class="mcp-edit">
        <KsForm
            ref="formRef"
            :model="form"
            labelPosition="top"
            @submit.prevent="save"
        >
            <KsFormItem
                :label="$t('mcp.server_id')"
                prop="id"
                required
                labelPosition="left"
                class="field"
                inline
                :rules="idRules"
            >
                <KsInput
                    v-model="form.id"
                    :placeholder="$t('mcp.id_placeholder')"
                    :disabled="idDisabled"
                    class="mono id-input"
                >
                    <template
                        v-if="idDisabled"
                        #suffix
                    >
                        <Lock :size="16" />
                    </template>
                </KsInput>
            </KsFormItem>

            <KsFormItem class="field" :label="$t('description')">
                <KsInput
                    v-model="form.description"
                    type="textarea"
                    :rows="2"
                    resize="none"
                    thinScrollbar
                    :placeholder="$t('description')"
                    :disabled="readOnly"
                />
            </KsFormItem>

            <KsFormItem class="field" :label="$t('mcp.instructions')">
                <KsInput
                    v-model="form.instructions"
                    type="textarea"
                    :rows="3"
                    resize="none"
                    thinScrollbar
                    :placeholder="$t('mcp.instructions')"
                    class="mono"
                    :disabled="readOnly"
                />
            </KsFormItem>

            <KsFormItem
                :label="$t('mcp.private_server')"
                labelPosition="left"
                class="field spread-row"
                inline
            >
                <KsSwitch
                    v-model="privateServer"
                    :disabled="readOnly"
                />
            </KsFormItem>

            <KsAlert
                v-if="!isPrivate"
                type="warning"
                :closable="false"
                class="type-hint"
            >
                {{ $t("mcp.public_hint") }}
            </KsAlert>

            <KsFormItem v-if="isPrivate" class="field">
                <KsRadioCardGroup
                    v-model="form.authType"
                    :options="authOptions"
                    :ariaLabel="$t('mcp.auth_type')"
                />
            </KsFormItem>

            <KsFormItem
                v-if="isOAuth"
                class="field"
                :label="$t('mcp.oauth_provider')"
                prop="oauthProvider"
                :rules="oauthProviderRules"
            >
                <KsSelect
                    v-model="form.oauthProvider"
                    :placeholder="$t('mcp.oauth_provider_placeholder')"
                    :disabled="readOnly"
                    class="full-width"
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
                class="field"
                :label="$t('mcp.scopes_supported')"
            >
                <KsSelect
                    v-model="form.oauthScopesSupported"
                    multiple
                    filterable
                    allowCreate
                    defaultFirstOption
                    :placeholder="$t('mcp.scopes_supported_placeholder')"
                    :disabled="readOnly"
                    class="full-width"
                />
                <div class="field-hint">
                    {{ $t("mcp.scopes_supported_hint") }}
                </div>
            </KsFormItem>

            <KsFormItem
                :label="$t('enabled')"
                labelPosition="left"
                class="field spread-row"
                inline
            >
                <KsSwitch
                    v-model="enabled"
                    :disabled="readOnly"
                />
            </KsFormItem>

            <div
                v-if="canSave"
                class="form-actions"
            >
                <KsButton @click="cancel">
                    {{ $t("cancel") }}
                </KsButton>
                <KsButton
                    type="primary"
                    :disabled="submitting"
                    @click="save"
                >
                    {{ isUpdate ? $t("save") : $t("create") }}
                </KsButton>
            </div>
        </KsForm>
    </div>
</template>

<script lang="ts" setup>
    import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"

    import {useMcpStore, type McpServerPayload} from "../../../../stores/mcp"
    import {useHelpers} from "../useHelpers"
    import {useMiscStore} from "override/stores/misc"
    import {useAuthStore} from "override/stores/auth"

    import {useToast} from "../../../../utils/toast"
    import {useUnsavedChangesStore} from "../../../../stores/unsavedChanges"

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
    const {listRoute} = useHelpers()

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
    const savedForm = ref(JSON.stringify(form.value))
    const unsavedChangesStore = useUnsavedChangesStore()
    const markSaved = () => savedForm.value = JSON.stringify(form.value)

    watch(() => JSON.stringify(form.value) !== savedForm.value, (dirty) => {
        unsavedChangesStore.unsavedChange = dirty
    })

    onBeforeUnmount(() => {
        unsavedChangesStore.unsavedChange = false
    })
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
        {required: true, message: t("is required", {field: t("id")}), trigger: "blur"},
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

    const save = async (): Promise<void> => {
        if (readOnly.value || !formRef.value || submitting.value) {
            return
        }

        await formRef.value.validate(async (valid) => {
            if (!valid) {
                return
            }

            submitting.value = true
            try {
                if (isUpdate.value) {
                    await mcpStore.update(form.value.id, buildPayload())
                    markSaved()
                    toast.saved(form.value.id)
                } else {
                    const created = await mcpStore.create(buildPayload())
                    markSaved()
                    toast.saved(created.id)
                    router.push({
                        name: "admin/mcp-servers/update",
                        params: {id: created.id, tab: "edit", tenant: route.params.tenant},
                    })
                }
            } catch (e) {
                console.error("Failed to save MCP server", e)
            } finally {
                submitting.value = false
            }
        }).catch(() => {})
    }

    const cancel = (): void => {
        markSaved()
        router.push(listRoute.value)
    }

    onMounted(() => {
        if (!authStore.auths) {
            authStore.loadAuths()
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
                markSaved()
            } else if (!isUpdate.value) {
                form.value = defaultForm()
                markSaved()
            }
        },
        {immediate: true},
    )
</script>

<style scoped>
    .mcp-edit {
        --ks-form-item-label-font-weight: var(--ks-font-weight-semibold);
        max-width: 653px;
        border: 1px solid var(--ks-border-default);
        border-radius: 8px;
        box-shadow: 0px 2px 8px 0px var(--ks-shadow-surface);
        background: var(--ks-bg-surface);
        padding: var(--ks-spacing-4);
        margin-block-start: var(--ks-spacing-7);
        margin-inline: auto;
    }

    .mono {
        font-family: var(--ks-font-family-mono);
    }

    .field:not(:first-child) {
        border-top: 1px solid var(--ks-border-subtle);
        padding-top: var(--ks-spacing-4);
    }

    .spread-row:last-child {
        margin-bottom: 0;
    }

    .form-actions {
        display: flex;
        justify-content: flex-end;
        gap: var(--ks-spacing-2);
        border-top: 1px solid var(--ks-border-subtle);
        padding-top: var(--ks-spacing-4);
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