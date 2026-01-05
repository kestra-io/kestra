<template>
    <el-card>
        <template #header>
            <div class="d-flex justify-content-between">
                <span class="d-inline-flex title align-items-center">
                    <AiIcon /><span>{{ $t("ai.flow.title") }}</span>
                </span>
                <el-button 
                    class="border-0 ai-close-button" 
                    size="small" 
                    :icon="Close" 
                    @click.stop="emit('close')" 
                />
            </div>
        </template>
        <el-input
            autosize
            ref="promptInput"
            v-if="configured"
            type="textarea"
            :placeholder="$t('ai.flow.prompt_placeholder')"
            v-model="prompt"
            @keydown.exact.ctrl.enter="$event.preventDefault(); prompt += '\n'"
            @keydown.exact.enter.prevent="submitPrompt"
            class="ai-copilot-placeholder"
        />
        <template v-else>
            <!-- eslint-disable-next-line vue/no-v-text-v-html-on-component -->
            <el-text class="keep-whitespace" v-html="$t('ai.flow.enable_instructions.header')" />
            <div class="mt-2" v-html="highlightedAiConfiguration" />
            <!-- eslint-disable-next-line vue/no-v-text-v-html-on-component -->
            <el-text class="keep-whitespace" v-html="$t('ai.flow.enable_instructions.footer')" />
        </template>
        <template #footer>
            <div class="d-flex justify-content-between">
                <el-text class="text-tertiary" size="small">
                    (⌘) Ctrl + Alt (⌥) + Shift + K {{ $t("to toggle") }}
                </el-text>
                <div v-if="configured" class="d-flex flex-column align-items-end gap-3">
                    <el-text v-if="error !== undefined" type="danger" size="default" class="me-auto">
                        {{ error }}
                    </el-text>
                    <div v-if="waitingForReply" class="d-flex loading-text">
                        <div v-loading="true" />
                        <span>{{ $t('ai.flow.generating') }}</span>
                    </div>
                    <el-button
                        v-else
                        type="primary"
                        :icon="KeyboardReturn"
                        :disabled="prompt.length === 0"
                        @click="submitPrompt"
                    >
                        {{ $t('submit') }}
                    </el-button>
                </div>
            </div>
        </template>
    </el-card>
</template>

<script setup lang="ts">
    import {computed, onMounted, onUnmounted, ref, watch} from "vue";
    import Close from "vue-material-design-icons/Close.vue";
    import KeyboardReturn from "vue-material-design-icons/KeyboardReturn.vue";
    import AiIcon from "./AiIcon.vue";
    import {useAiStore} from "../../stores/ai";
    import Utils from "../../utils/utils";
    import {useMiscStore} from "override/stores/misc";

    const aiStore = useAiStore();
    const emit = defineEmits<{
        close: [];
        generatedYaml: [string];
    }>();

    const promptInput = ref<HTMLInputElement>();

    onMounted(() => {
        promptInput.value?.focus();
    })

    onUnmounted(() => {
        sessionStorage.removeItem("kestra-ai-prompt");
    })

    const prompt = ref(sessionStorage.getItem("kestra-ai-prompt") ?? "");
    const waitingForReply = ref(false);

    watch(prompt, (newValue) => {
        sessionStorage.setItem("kestra-ai-prompt", newValue);
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

    // Enhanced close button animation
    .ai-close-button {
        transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
        
        &:hover {
            transform: translateY(-2px);
            opacity: 0.8;
        }
        
        &:active {
            transform: translateY(0);
            opacity: 0.6;
        }
    }

    // Staggered animations for children elements (scaleX only, faster)
    :deep(.el-card__header) {
        animation: scaleInX 0.30s cubic-bezier(0.2, 0.8, 0.2, 1) 0.04s both;
    }

    :deep(.el-card__body) {
        animation: scaleInX 0.30s cubic-bezier(0.2, 0.8, 0.2, 1) 0.08s both;
    }

    :deep(.el-card__footer) {
        animation: scaleInX 0.30s cubic-bezier(0.2, 0.8, 0.2, 1) 0.12s both;
    }

    @keyframes scaleInX {
        from {
            opacity: 0;
            transform: scaleX(0.85);
        }
        to {
            opacity: 1;
            transform: scaleX(1);
        }
    }
</style>
