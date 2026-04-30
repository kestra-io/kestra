<template>
    <TopNavBar :title="t('mcp.servers')" :longDescription="t('mcp.page_description')" />

    <section class="container">
        <div class="d-flex justify-content-end mb-4 mt-3">
            <el-button type="primary" @click="openCreateModal">
                + {{ t("mcp.create") }}
            </el-button>
        </div>

        <div v-if="loading" class="d-flex justify-content-center py-5">
            <el-icon class="is-loading" :size="32">
                <Loading />
            </el-icon>
        </div>

        <KsEmpty v-else-if="servers.length === 0" :description="t('mcp.no_servers')" />

        <div v-else class="mcp-grid">
            <div
                v-for="server in servers"
                :key="server.id"
                class="mcp-card"
                :class="{'mcp-card--disabled': !server.enabled}"
            >
                <!-- Top row -->
                <div class="mcp-card__header">
                    <div class="mcp-card__icon">
                        <ServerNetwork class="mcp-card__icon-svg" />
                    </div>
                    <div class="mcp-card__title-group">
                        <span class="mcp-card__name">{{ server.id }}</span>
                        <div class="d-flex align-items-center gap-2">
                            <el-tooltip :content="server.serverType === 'PRIVATE' ? t('mcp.private') : t('mcp.public')">
                                <Lock v-if="server.serverType === 'PRIVATE'" class="mcp-card__type-icon" />
                                <Web v-else class="mcp-card__type-icon" />
                            </el-tooltip>
                            <span class="mcp-card__auth-badge">{{ server.authType.replace("_", " ") }}</span>
                        </div>
                    </div>
                    <el-switch
                        class="mcp-card__toggle ms-auto"
                        :modelValue="server.enabled"
                        @change="toggleServer(server)"
                    />
                </div>

                <!-- Instructions -->
                <div v-if="server.instructions" class="mcp-card__prompt-section">
                    <div class="mcp-card__prompt-label">
                        {{ t("mcp.instructions") }}
                    </div>
                    <div class="mcp-card__prompt-text">
                        {{ server.instructions }}
                    </div>
                </div>

                <!-- Actions bar -->
                <div class="mcp-card__actions">
                    <el-button size="small" type="primary" plain @click="openConnectModal(server)">
                        {{ t("mcp.connect") }}
                    </el-button>
                    <el-button size="small" plain @click="copyUrl(server)">
                        {{ t("mcp.copy_url") }}
                    </el-button>
                    <div class="mcp-card__actions-right">
                        <el-tooltip :content="t('edit')">
                            <el-button size="small" circle plain @click="openEditModal(server)">
                                <Pencil />
                            </el-button>
                        </el-tooltip>
                        <el-tooltip v-if="!server.isDefault" :content="t('delete')">
                            <el-button size="small" circle plain class="mcp-card__delete-btn" @click="confirmDelete(server)">
                                <TrashCan />
                            </el-button>
                        </el-tooltip>
                    </div>
                </div>
            </div>
        </div>

        <!-- Create / Edit Modal -->
        <Modal
            v-model="showModal"
            :title="editingServer ? t('mcp.server') : t('mcp.create')"
        >
            <el-form
                ref="formRef"
                :model="form"
                labelPosition="top"
                @submit.prevent="saveServer"
            >
                <el-form-item
                    :label="t('id')"
                    prop="id"
                    :rules="[{required: true, message: t('id') + ' ' + t('required'), trigger: 'blur'}]"
                >
                    <el-input
                        v-model="form.id"
                        :placeholder="'e.g. my-mcp-server'"
                        :disabled="!!editingServer?.isDefault"
                        class="mcp-modal__name-input"
                    />
                </el-form-item>

                <el-form-item :label="t('description')">
                    <el-input
                        v-model="form.description"
                        type="textarea"
                        :rows="2"
                        :placeholder="t('description')"
                    />
                </el-form-item>

                <el-form-item :label="t('mcp.instructions')">
                    <el-input
                        v-model="form.instructions"
                        type="textarea"
                        :rows="3"
                        :placeholder="t('mcp.instructions')"
                        class="mcp-modal__prompt-input"
                    />
                </el-form-item>

                <el-form-item :label="t('mcp.server_type')">
                    <div class="mcp-modal__type-buttons">
                        <button
                            type="button"
                            class="mcp-modal__type-btn"
                            :class="{'mcp-modal__type-btn--active': form.serverType === 'PRIVATE'}"
                            @click="form.serverType = 'PRIVATE'"
                        >
                            <Lock class="me-1" />
                            {{ t("mcp.private") }}
                        </button>
                        <button
                            type="button"
                            class="mcp-modal__type-btn"
                            :class="{'mcp-modal__type-btn--active': form.serverType === 'PUBLIC'}"
                            @click="form.serverType = 'PUBLIC'"
                        >
                            <Web class="me-1" />
                            {{ t("mcp.public") }}
                        </button>
                    </div>
                </el-form-item>

                <el-form-item v-if="form.serverType === 'PRIVATE'" :label="t('mcp.auth_type')">
                    <div class="mcp-modal__auth-list">
                        <label
                            v-for="opt in AUTH_OPTIONS"
                            :key="opt.value"
                            class="mcp-modal__auth-option"
                            :class="{
                                'mcp-modal__auth-option--selected': form.authType === opt.value,
                                'mcp-modal__auth-option--disabled': opt.ee && isOss,
                            }"
                        >
                            <input type="radio" :value="opt.value" v-model="form.authType" class="me-2" :disabled="opt.ee && isOss">
                            <span class="mcp-modal__auth-name">{{ t(opt.labelKey) }}</span>
                            <LockOutline v-if="opt.ee && isOss" class="ms-2" :size="14" />
                            <span class="mcp-modal__auth-hint ms-auto">{{ t(opt.hintKey) }}</span>
                        </label>
                    </div>
                </el-form-item>
            </el-form>

            <template #footer>
                <el-button @click="showModal = false">
                    {{ t("cancel") }}
                </el-button>
                <el-button type="primary" @click="saveServer">
                    {{ editingServer ? t("mcp.save") : t("mcp.create") }}
                </el-button>
            </template>
        </Modal>

        <!-- Connect Modal -->
        <Modal
            v-model="showConnectModal"
            :title="connectingServer ? t('mcp.connect_modal.title', {name: connectingServer.id}) : ''"
            width="640px"
        >
            <div v-if="connectingServer" class="mcp-connect">
                <!-- Server URL -->
                <div class="mcp-connect__section">
                    <div class="mcp-connect__section-title">
                        {{ t("mcp.connect_modal.server_url") }}
                    </div>
                    <div class="mcp-connect__code-block">
                        <code>{{ sseUrl(connectingServer) }}</code>
                        <el-button size="small" class="mcp-connect__copy-btn" @click="copyToClipboard(sseUrl(connectingServer), 'url')">
                            <Check v-if="copiedKey === 'url'" class="text-success" />
                            <ContentCopy v-else />
                        </el-button>
                    </div>
                </div>

                <!-- Claude Desktop -->
                <div class="mcp-connect__section">
                    <div class="mcp-connect__client-header">
                        <span class="mcp-connect__badge mcp-connect__badge--claude">C</span>
                        <span class="mcp-connect__client-name">{{ t("mcp.connect_modal.claude_desktop") }}</span>
                    </div>
                    <p class="mcp-connect__hint">
                        {{ t("mcp.connect_modal.claude_desktop_hint") }}
                    </p>
                    <div class="mcp-connect__code-block">
                        <pre class="mcp-connect__pre">{{ claudeDesktopConfig }}</pre>
                        <el-button size="small" class="mcp-connect__copy-btn" @click="copyToClipboard(claudeDesktopConfig, 'desktop')">
                            <Check v-if="copiedKey === 'desktop'" class="text-success" />
                            <ContentCopy v-else />
                        </el-button>
                    </div>
                    <p class="mcp-connect__file-hint">
                        {{ t("mcp.connect_modal.claude_desktop_mac") }}
                    </p>
                    <p class="mcp-connect__file-hint">
                        {{ t("mcp.connect_modal.claude_desktop_win") }}
                    </p>
                </div>

                <!-- Claude Code -->
                <div class="mcp-connect__section">
                    <div class="mcp-connect__client-header">
                        <span class="mcp-connect__badge mcp-connect__badge--claude">C</span>
                        <span class="mcp-connect__client-name">{{ t("mcp.connect_modal.claude_code") }}</span>
                    </div>
                    <p class="mcp-connect__hint">
                        {{ t("mcp.connect_modal.claude_code_hint") }}
                    </p>
                    <div class="mcp-connect__code-block">
                        <pre class="mcp-connect__pre">{{ claudeCodeCommand }}</pre>
                        <el-button size="small" class="mcp-connect__copy-btn" @click="copyToClipboard(claudeCodeCommand, 'code')">
                            <Check v-if="copiedKey === 'code'" class="text-success" />
                            <ContentCopy v-else />
                        </el-button>
                    </div>
                </div>

                <!-- Cursor -->
                <div class="mcp-connect__section">
                    <div class="mcp-connect__client-header">
                        <span class="mcp-connect__badge mcp-connect__badge--cursor">◈</span>
                        <span class="mcp-connect__client-name">{{ t("mcp.connect_modal.cursor") }}</span>
                    </div>
                    <p class="mcp-connect__hint">
                        {{ t("mcp.connect_modal.cursor_hint") }}
                    </p>
                    <div class="mcp-connect__code-block">
                        <pre class="mcp-connect__pre">{{ cursorConfig }}</pre>
                        <el-button size="small" class="mcp-connect__copy-btn" @click="copyToClipboard(cursorConfig, 'cursor')">
                            <Check v-if="copiedKey === 'cursor'" class="text-success" />
                            <ContentCopy v-else />
                        </el-button>
                    </div>
                </div>

                <!-- Codex -->
                <div class="mcp-connect__section">
                    <div class="mcp-connect__client-header">
                        <span class="mcp-connect__badge mcp-connect__badge--codex">O</span>
                        <span class="mcp-connect__client-name">{{ t("mcp.connect_modal.codex") }}</span>
                    </div>
                    <p class="mcp-connect__hint">
                        {{ t("mcp.connect_modal.codex_hint") }}
                    </p>
                    <div class="mcp-connect__code-block">
                        <pre class="mcp-connect__pre">{{ codexConfig }}</pre>
                        <el-button size="small" class="mcp-connect__copy-btn" @click="copyToClipboard(codexConfig, 'codex')">
                            <Check v-if="copiedKey === 'codex'" class="text-success" />
                            <ContentCopy v-else />
                        </el-button>
                    </div>
                </div>
            </div>

            <template #footer>
                <el-button type="primary" @click="showConnectModal = false">
                    {{ t("mcp.connect_modal.done") }}
                </el-button>
            </template>
        </Modal>
    </section>
</template>

<script lang="ts" setup>
    import {computed, onMounted, ref} from "vue";
    import {useI18n} from "vue-i18n";
    import type {FormInstance} from "element-plus";
    import {useMiscStore} from "override/stores/misc";
    import TopNavBar from "../layout/TopNavBar.vue";
    import Modal from "../Modal.vue";
    import LockOutline from "vue-material-design-icons/LockOutline.vue";
    import useRouteContext from "../../composables/useRouteContext";
    import {useMcpStore, type McpServer} from "../../stores/mcp";
    import {baseUrl} from "override/utils/route";
    import ServerNetwork from "vue-material-design-icons/ServerNetwork.vue";
    import Lock from "vue-material-design-icons/Lock.vue";
    import Web from "vue-material-design-icons/Web.vue";
    import Pencil from "vue-material-design-icons/Pencil.vue";
    import TrashCan from "vue-material-design-icons/TrashCan.vue";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import Check from "vue-material-design-icons/Check.vue";
    import Loading from "vue-material-design-icons/Loading.vue";

    const {t} = useI18n({useScope: "global"});

    const routeInfo = computed(() => ({title: t("mcp.servers")}));
    useRouteContext(routeInfo);

    const mcpStore = useMcpStore();
    const isOss = computed(() => useMiscStore().configs?.edition === "OSS");

    interface McpForm {
        id: string;
        description: string;
        instructions: string;
        serverType: "PRIVATE" | "PUBLIC";
        authType: "BASIC" | "API_TOKEN";
    }

    const AUTH_OPTIONS = [
        {value: "BASIC" as const, labelKey: "mcp.basic_auth", hintKey: "mcp.username_password", ee: false},
        {value: "API_TOKEN" as const, labelKey: "mcp.api_token", hintKey: "mcp.bearer_token", ee: true},
    ];

    const servers = ref<McpServer[]>([]);
    const loading = ref(false);

    const showModal = ref(false);
    const editingServer = ref<McpServer | null>(null);
    const formRef = ref<FormInstance>();
    const form = ref<McpForm>({
        id: "",
        description: "",
        instructions: "",
        serverType: "PRIVATE",
        authType: "BASIC",
    });

    const showConnectModal = ref(false);
    const connectingServer = ref<McpServer | null>(null);
    const copiedKey = ref<string | null>(null);

    const loadServers = async (): Promise<void> => {
        loading.value = true;
        try {
            const result = await mcpStore.list();
            servers.value = result?.results ?? [];
        } finally {
            loading.value = false;
        }
    };

    const sseUrl = (server: McpServer): string => {
        const base = baseUrl.startsWith("http") ? baseUrl : `${window.location.origin}${baseUrl}`;
        return `${base}/api/v1/main/mcp/${server.id}`;
    };

    const authorizationHeader = (server: McpServer): string | null => {
        if (server.serverType === "PUBLIC") return null;
        if (server.authType === "BASIC") return "Basic <base64(username:password)>";
        if (server.authType === "API_TOKEN") return "Bearer <your-api-token>";
        return null;
    };

    const claudeDesktopConfig = computed(() => {
        if (!connectingServer.value) return "";
        const server = connectingServer.value;
        const authHeader = authorizationHeader(server);
        const args: string[] = ["mcp-remote", sseUrl(server)];
        const entry: Record<string, unknown> = {command: "npx", args};
        if (authHeader) {
            args.push("--header", "Authorization: ${AUTH_HEADER}");
            entry.env = {AUTH_HEADER: authHeader};
        }
        return JSON.stringify({mcpServers: {[server.id]: entry}}, null, 2);
    });

    const claudeCodeCommand = computed(() => {
        if (!connectingServer.value) return "";
        const server = connectingServer.value;
        const authHeader = authorizationHeader(server);
        const lines = ["claude mcp add \\", "  --transport http \\"];
        if (authHeader) {
            lines.push(`  --header "Authorization: ${authHeader}" \\`);
        }
        lines.push(`  ${server.id} \\`);
        lines.push(`  ${sseUrl(server)}`);
        return lines.join("\n");
    });

    const cursorConfig = computed(() => {
        if (!connectingServer.value) return "";
        const server = connectingServer.value;
        const entry: Record<string, unknown> = {url: sseUrl(server)};
        const authHeader = authorizationHeader(server);
        if (authHeader) {
            entry.headers = {Authorization: authHeader};
        }
        return JSON.stringify({mcpServers: {[server.id]: entry}}, null, 2);
    });

    const codexConfig = computed(() => {
        if (!connectingServer.value) return "";
        const server = connectingServer.value;
        const entry: Record<string, unknown> = {url: sseUrl(server)};
        const authHeader = authorizationHeader(server);
        if (authHeader) {
            entry.headers = {Authorization: authHeader};
        }
        return JSON.stringify({mcpServers: {[server.id]: entry}}, null, 2);
    });

    const resetForm = (): void => {
        form.value = {
            id: "",
            description: "",
            instructions: "",
            serverType: "PRIVATE",
            authType: "BASIC",
        };
    };

    const openCreateModal = (): void => {
        editingServer.value = null;
        resetForm();
        showModal.value = true;
    };

    const openEditModal = (server: McpServer): void => {
        editingServer.value = server;
        form.value = {
            id: server.id,
            description: server.description ?? "",
            instructions: server.instructions ?? "",
            serverType: server.serverType,
            authType: server.authType,
        };
        showModal.value = true;
    };

    const saveServer = async (): Promise<void> => {
        if (!formRef.value) return;
        await formRef.value.validate(async (valid) => {
            if (!valid) return;
            const payload = {
                id: form.value.id,
                description: form.value.description || undefined,
                instructions: form.value.instructions || undefined,
                serverType: form.value.serverType,
                authType: form.value.authType,
                enabled: editingServer.value?.enabled ?? false,
            };
            if (editingServer.value) {
                await mcpStore.update(editingServer.value.id, payload);
            } else {
                await mcpStore.create(payload);
            }

            showModal.value = false;
            await loadServers();
        });
    };

    const toggleServer = async (server: McpServer): Promise<void> => {
        await mcpStore.toggle(server.id);
        await loadServers();
    };

    const confirmDelete = (server: McpServer): void => {
        if (!confirm(t("mcp.delete_confirm"))) return;
        deleteServer(server);
    };

    const deleteServer = async (server: McpServer): Promise<void> => {
        await mcpStore.remove(server.id);
        await loadServers();
    };

    const openConnectModal = (server: McpServer): void => {
        connectingServer.value = server;
        copiedKey.value = null;
        showConnectModal.value = true;
    };

    const copyUrl = async (server: McpServer): Promise<void> => {
        await navigator.clipboard.writeText(sseUrl(server));
    };

    const copyToClipboard = async (text: string, key: string): Promise<void> => {
        await navigator.clipboard.writeText(text);
        copiedKey.value = key;
        setTimeout(() => { copiedKey.value = null; }, 2000);
    };

    onMounted(loadServers);
</script>

<style lang="scss" scoped>
  @import "@kestra-io/ui-libs/src/scss/color-palette.scss";

  .mcp-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 1.25rem;

    @media (max-width: 1200px) {
      grid-template-columns: repeat(2, 1fr);
    }

    @media (max-width: 768px) {
      grid-template-columns: 1fr;
    }
  }

  .mcp-card {
    border: 1px solid var(--ks-border-primary);
    border-radius: 8px;
    background: var(--ks-background-card);
    display: flex;
    flex-direction: column;
    overflow: hidden;
    transition: opacity 0.2s;

    &--disabled {
      opacity: 0.6;
    }

    &__header {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 1rem;
    }

    &__icon {
      width: 40px;
      height: 40px;
      border-radius: 8px;
      background: var(--ks-background-body);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    &__icon-svg {
      color: var(--ks-content-secondary);
      font-size: 1.25rem;
    }

    &__title-group {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
      min-width: 0;
    }

    &__name {
      font-weight: 600;
      font-size: 0.9375rem;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    &__type-icon {
      color: var(--ks-content-secondary);
      font-size: 0.875rem;
    }

    &__auth-badge {
      font-size: 0.6875rem;
      font-weight: 600;
      letter-spacing: 0.05em;
      text-transform: uppercase;
      color: var(--ks-content-secondary);
      background: var(--ks-background-body);
      border: 1px solid var(--ks-border-primary);
      border-radius: 4px;
      padding: 1px 5px;
    }

    &__toggle {
      --el-switch-on-color: #{$base-purple-400};
    }

    &__prompt-section {
      margin: 0 1rem;
      padding: 0.625rem;
      background: var(--ks-background-body);
      border-radius: 6px;
      border: 1px solid var(--ks-border-primary);
    }

    &__prompt-label {
      font-size: 0.625rem;
      font-weight: 700;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--ks-content-tertiary);
      margin-bottom: 0.25rem;
    }

    &__prompt-text {
      font-family: monospace;
      font-size: 0.75rem;
      color: var(--ks-content-secondary);
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
      line-height: 1.5;
    }

    &__actions {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.75rem 1rem;
      margin-top: auto;
      border-top: 1px solid var(--ks-border-primary);
    }

    &__actions-right {
      margin-left: auto;
      display: flex;
      gap: 0.25rem;
    }

    &__delete-btn {
      color: var(--el-color-danger) !important;
      border-color: var(--el-color-danger) !important;

      &:hover {
        background: var(--el-color-danger-light-9) !important;
      }
    }
  }

  // Create/Edit modal
  .mcp-modal {
    &__id-row {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      width: 100%;
      background: var(--ks-background-body);
      border: 1px solid var(--ks-border-primary);
      border-radius: 6px;
      padding: 0.375rem 0.75rem;
    }

    &__id-code {
      flex: 1;
      font-family: monospace;
      font-size: 0.8125rem;
      color: var(--ks-content-primary);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    &__name-input {
      :deep(input) {
        font-family: monospace;
      }
    }

    &__prompt-input {
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

      &:hover:not(.mcp-modal__type-btn--active) {
        border-color: var(--ks-border-secondary);
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

      &:hover:not(.mcp-modal__auth-option--selected):not(.mcp-modal__auth-option--disabled) {
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

  }

  // Connect modal
  .mcp-connect {
    display: flex;
    flex-direction: column;
    gap: 1.25rem;

    &__section {
      border: 1px solid var(--ks-border-primary);
      border-radius: 8px;
      padding: 0.875rem;
    }

    &__section-title {
      font-size: 0.75rem;
      font-weight: 700;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      color: var(--ks-content-secondary);
      margin-bottom: 0.5rem;
    }

    &__client-header {
      display: flex;
      align-items: center;
      gap: 0.625rem;
      margin-bottom: 0.5rem;
    }

    &__client-name {
      font-weight: 600;
    }

    &__badge {
      width: 24px;
      height: 24px;
      border-radius: 6px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-size: 0.75rem;
      font-weight: 700;
      flex-shrink: 0;
      color: #fff;

      $badge-colors: (claude: #e8440a, cursor: #1a1a1a, codex: #10a37f);
      @each $name, $color in $badge-colors {
        &--#{$name} { background: $color; }
      }
    }

    &__hint {
      font-size: 0.8125rem;
      color: var(--ks-content-secondary);
      margin-bottom: 0.5rem;
    }

    &__file-hint {
      font-size: 0.75rem;
      color: var(--ks-content-tertiary);
      margin-top: 0.25rem;
      margin-bottom: 0;
    }

    &__code-block {
      display: flex;
      align-items: center;
      background: var(--ks-background-body);
      border: 1px solid var(--ks-border-primary);
      border-radius: 6px;
      padding: 0.5rem 0.75rem;
      gap: 0.5rem;

      code, pre {
        flex: 1;
        font-size: 0.8125rem;
        color: var(--ks-content-primary);
        overflow-x: auto;
        white-space: pre-wrap;
        word-break: break-all;
        margin: 0;
        background: none;
        padding: 0;
        font-family: monospace;
      }
    }

    &__copy-btn {
      flex-shrink: 0;
    }
  }
</style>
