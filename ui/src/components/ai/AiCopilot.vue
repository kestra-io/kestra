<template>
    <el-card>
        <template #header>
            <div class="d-flex justify-content-between">
                <span class="d-inline-flex title align-items-center">
                    <AiIcon /><span>{{ t("ai.flow.title") }}</span>
                </span>
                <el-button class="border-0" size="small" :icon="Close" @click.stop="emit('close')" />
            </div>
        </template>
        <el-input
            autosize
            ref="promptInput"
            v-if="configured"
            type="textarea"
            :placeholder="t('ai.flow.prompt_placeholder')"
            v-model="prompt"
            @keydown.exact.ctrl.enter="$event.preventDefault(); prompt += '\n'"
            @keydown.exact.enter.prevent="submitPrompt"
            class="ai-copilot-placeholder"
        />
        <template v-else>
            <!-- eslint-disable-next-line vue/no-v-text-v-html-on-component -->
            <el-text class="keep-whitespace" v-html="t('ai.flow.enable_instructions.header')" />
            <div class="mt-2" v-html="highlightedAiConfiguration" />
            <!-- eslint-disable-next-line vue/no-v-text-v-html-on-component -->
            <el-text class="keep-whitespace" v-html="t('ai.flow.enable_instructions.footer')" />
        </template>
        <template #footer>
            <div class="d-flex justify-content-between">
                <el-text class="text-tertiary" size="small">
                    (⌘) Ctrl + Alt (⌥) + Shift + K {{ t("to toggle") }}
                </el-text>
                <div v-if="configured" class="d-flex flex-column align-items-end gap-3">
                    <el-text v-if="error !== undefined" type="danger" size="default" class="me-auto">
                        {{ error }}
                    </el-text>
                    <div v-if="waitingForReply" class="d-flex loading-text">
                        <div v-loading="true" />
                        <span>{{ t('ai.flow.generating') }}</span>
                    </div>
                    <el-button
                        v-else
                        type="primary"
                        :icon="KeyboardReturn"
                        :disabled="prompt.length === 0"
                        @click="submitPrompt"
                    >
                        {{ t('submit') }}
                    </el-button>
                </div>
            </div>
        </template>
    </el-card>
</template>

<script setup lang="ts">
    import {computed, getCurrentInstance, onMounted, ref, watch} from "vue";
    import Close from "vue-material-design-icons/Close.vue";
    import KeyboardReturn from "vue-material-design-icons/KeyboardReturn.vue";
    import AiIcon from "./AiIcon.vue";
    import {useAiStore} from "../../stores/ai";
    import Utils from "../../utils/utils";
    import {useMiscStore} from "override/stores/misc";

    const t = getCurrentInstance()!.appContext.config.globalProperties.$t;
    const aiStore = useAiStore();
    const emit = defineEmits<{
        close: [];
        generatedYaml: [string];
    }>();

    const promptInput = ref<HTMLInputElement>();

    function simpleHash(value: string): string {
        // simple deterministic hash (djb2)
        let hash = 5381;
        for (let i = 0; i < value.length; i++) {
            hash = ((hash << 5) + hash) + value.charCodeAt(i); /* hash * 33 + c */
            hash = hash & hash;
        }
        return String(hash >>> 0);
    }

    const prompt = ref("");
    const waitingForReply = ref(false);

    watch(prompt, (newValue) => {
        try {
            sessionStorage.setItem("kestra-ai-prompt", newValue);
            // persist the flow-hash associated with this prompt (so prompts don't leak across different flows)
            const flowHash = simpleHash(props.flow ?? "");
            sessionStorage.setItem("kestra-ai-prompt-flow-hash", flowHash);
        } catch (e) {
            // ignore sessionStorage errors (e.g., privacy mode)
        }
    });

    // When mounting, clear any persisted prompt that belongs to a different flow.
    onMounted(() => {
        promptInput.value?.focus();

        try {
            const stored = sessionStorage.getItem("kestra-ai-prompt");
            const storedHash = sessionStorage.getItem("kestra-ai-prompt-flow-hash");
            const currentHash = simpleHash(props.flow ?? "");

            // If a stored prompt exists and it's associated with a flow hash that's different
            // from the current flow, clear it. If there's no storedHash, keep the prompt
            // (this preserves intentional pre-filled prompts coming from other pages, e.g. TaskRunLine).
            if (stored && storedHash && storedHash !== currentHash) {
                prompt.value = "";
                sessionStorage.removeItem("kestra-ai-prompt");
                sessionStorage.removeItem("kestra-ai-prompt-flow-hash");
            } else if (stored) {
                // Only set the prompt if it belongs to the current flow
                prompt.value = stored;
            }
        } catch (e) {
            // ignore storage errors
        }
    });

    // Reset prompt when the provided flow changes and the persisted prompt belongs to a different flow
    watch(() => props.flow, (newFlow, oldFlow) => {
        if (newFlow === oldFlow) return;
        try {
            const storedHash = sessionStorage.getItem("kestra-ai-prompt-flow-hash");
            const currentHash = simpleHash(newFlow ?? "");

            // If there is an existing stored prompt associated with another flow, clear it.
            if (storedHash && storedHash !== currentHash) {
                prompt.value = "";
                sessionStorage.removeItem("kestra-ai-prompt");
                sessionStorage.removeItem("kestra-ai-prompt-flow-hash");
            }
        } catch (e) {
            // ignore storage errors
        }

        // refocus the input so the user can start typing immediately
        setTimeout(() => promptInput.value?.focus(), 0);
    });

    const props = defineProps<{
        flow: string,
        conversationId: string
    }>();

    const error = ref<string | undefined>(undefined);

    async function submitPrompt() {
        error.value = undefined;
        waitingForReply.value = true;

        let aiResponse;
        try {
            aiResponse = await aiStore.generateFlow({
                userPrompt: prompt.value,
                flowYaml: props.flow,
                conversationId: props.conversationId
            }) as string;
            emit("generatedYaml", aiResponse);
        } catch (e: any) {
            error.value = e.response?.data?.message as string ?? e;
        }

        waitingForReply.value = false;
    }

    const highlightedAiConfiguration = ref<string | undefined>();

    const miscStore = useMiscStore();
    const configured = computed(() => miscStore.configs?.isAiEnabled);

    onMounted(async () => {
        if (!configured.value) {
            const {
                createHighlighterCore,
                langs,
                githubDark,
                githubLight,
                onigurumaEngine
            } = await import("../../utils/markdownDeps");
            const highlighter = await createHighlighterCore({
                langs: [langs.yaml],
                themes: [githubDark, githubLight],
                engine: onigurumaEngine
            })
            highlightedAiConfiguration.value = highlighter.codeToHtml(`kestra:
  ai:
    type: "gemini"
    gemini:
      api-key: "geminiApiKey"
      model-name: gemini-2.5-flash`, {lang: "yaml", theme: Utils.getTheme() === "dark" ? "github-dark" : "github-light"})
        }
    });
</script>

<style scoped lang="scss">
    :deep(.el-card__header) {
        font-size: 12px;
        line-height: 1;
        border-bottom: none;

        .title :not(:first-child) {
            margin-left: 6px;
        }
    }

    :deep(.el-card__footer) {
        border-top: none;
    }

    .loading-text {
        :first-child {
            width: 20px;
            height: 20px;
            --el-loading-spinner-size: 20px;
        }

        :not(:first-child) {
            margin-left: 6px;
        }
    }

    .ai-copilot-placeholder :deep(textarea::placeholder) {
        color: gray;
        font-style: italic;
    }
</style>
