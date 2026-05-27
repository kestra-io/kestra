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
                            'mcp-edit__auth-option--disabled': readOnly || (opt.ee && isOss),
                        }"
                    >
                        <input
                            type="radio"
                            :value="opt.value"
                            v-model="form.authType"
                            class="me-2"
                            :disabled="readOnly || (opt.ee && isOss)"
                        >
                        <span class="mcp-edit__auth-name">{{ t(opt.labelKey) }}</span>
                        <LockOutline v-if="opt.ee && isOss" class="ms-2" :size="14" />
                        <span class="mcp-edit__auth-hint ms-auto">{{ t(opt.hintKey) }}</span>
                    </label>
                </div>
            </el-form-item>

            <el-form-item :label="t('enabled')">
                <el-switch
                    :modelValue="!form.disabled"
                    :disabled="readOnly"
                    @update:model-value="(val: boolean) => (form.disabled = !val)"
                    class="mcp-edit__toggle"
                />
            </el-form-item>

            <div class="mcp-edit__actions">
                <el-button v-if="canSave" type="primary" :loading="saving" @click="save">
                    {{ isUpdate ? t("mcp.save") : t("mcp.create") }}
                </el-button>
                <el-button
                    v-if="isUpdate && !mcpStore.server?.isDefault && canDelete"
                    type="danger"
                    plain
                    :loading="deleting"
                    @click="confirmDelete"
                >
                    {{ t("delete") }}
                </el-button>
            </div>
        </el-form>
    </div>
</template>

<script lang="ts" setup>
    import {computed, ref, watch} from "vue"
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
    const isOss = computed(() => useMiscStore().configs?.edition === "OSS")

    const isUpdate = computed(() => !!route.params.id)

    const authStore = useAuthStore()
    const canSave = computed(() =>
        isUpdate.value
            ? authStore.user?.hasAnyAction?.(resource.MCP_SERVER, action.UPDATE) ?? true
            : authStore.user?.hasAnyAction?.(resource.MCP_SERVER, action.CREATE) ?? true,
    )
    const canDelete = computed(() => authStore.user?.hasAnyAction?.(resource.MCP_SERVER, action.DELETE) ?? true)
    const readOnly = computed(() => !canSave.value)

    interface McpForm {
        id: string;
        description: string;
        instructions: string;
        serverType: "PRIVATE" | "PUBLIC";
        authType: "BASIC" | "API_TOKEN";
        disabled: boolean;
    }

    const AUTH_OPTIONS = [
        {value: "BASIC" as const, labelKey: "mcp.basic_auth", hintKey: "mcp.username_password", ee: false},
        {value: "API_TOKEN" as const, labelKey: "mcp.api_token", hintKey: "mcp.bearer_token", ee: true},
    ]

    const defaultForm = (): McpForm => ({
        id: "",
        description: "",
        instructions: "",
        serverType: "PRIVATE",
        authType: "BASIC",
        disabled: false,
    })

    const formRef = ref<FormInstance>()
    const form = ref<McpForm>(defaultForm())
    const saving = ref(false)
    const deleting = ref(false)

    watch(() => mcpStore.server, (server) => {
        if (server) {
            form.value = {
                id: server.id,
                description: server.description ?? "",
                instructions: server.instructions ?? "",
                serverType: server.serverType,
                authType: server.authType,
                disabled: server.disabled,
            }
        } else if (!isUpdate.value) {
            form.value = defaultForm()
        }
    }, {immediate: true})

    const save = async (): Promise<void> => {
        if (!formRef.value) return
        await formRef.value.validate(async (valid) => {
            if (!valid) return
            saving.value = true
            try {
                const payload = {
                    id: form.value.id,
                    description: form.value.description || undefined,
                    instructions: form.value.instructions || undefined,
                    serverType: form.value.serverType,
                    authType: form.value.authType,
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
            } finally {
                saving.value = false
            }
        })
    }

    const confirmDelete = async (): Promise<void> => {
        if (!confirm(t("mcp.delete_confirm"))) return
        deleting.value = true
        try {
            await mcpStore.remove(mcpStore.server!.id)
            router.push({name: "admin/mcp-servers"})
        } finally {
            deleting.value = false
        }
    }
</script>

<style lang="scss" scoped>
    @import "@kestra-io/ui-libs/src/scss/color-palette.scss";

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
            border: 1px solid var(--ks-border-primary);
            border-radius: 6px;
            background: var(--ks-background-card);
            color: var(--ks-content-primary);
            cursor: pointer;
            transition: all 0.15s;

            &--active {
                border-color: $base-purple-400;
                background: rgba($base-purple-400, 0.08);
                color: $base-purple-400;
            }

            &:hover:not(.mcp-edit__type-btn--active) {
                border-color: var(--ks-border-secondary);
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
            border: 1px solid var(--ks-border-primary);
            border-radius: 6px;
            cursor: pointer;
            transition: border-color 0.15s;

            &--selected {
                border-color: $base-purple-400;
                background: rgba($base-purple-400, 0.04);
            }

            &--disabled {
                opacity: 0.45;
                cursor: not-allowed;
            }

            &:hover:not(.mcp-edit__auth-option--selected):not(.mcp-edit__auth-option--disabled) {
                border-color: var(--ks-border-secondary);
            }
        }

        &__auth-name {
            font-weight: 500;
        }

        &__auth-hint {
            font-size: 0.8125rem;
            color: var(--ks-content-secondary);
        }

        &__toggle {
            --el-switch-on-color: #{$base-purple-400};
        }

        &__actions {
            display: flex;
            gap: 0.75rem;
            padding-top: 0.5rem;
        }
    }
</style>
