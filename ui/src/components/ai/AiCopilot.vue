<template>
    <el-card shadow="never" class="ai-copilot-card">
        <template #header>
            <div class="d-flex justify-content-between align-items-center">
                <span class="d-inline-flex title align-items-center">
                    <AiIcon />&nbsp;<span>{{ $t("ai.flow.title") }}</span>
                </span>
                <el-button
                    class="ai-close-button"
                    :icon="Close"
                    @click.stop="emit('close')"
                />
            </div>
        </template>

        <div class="ai-body">
            <template v-if="isListening">
                <div class="ai-voice-pill">
                    <div class="ai-waves-track" ref="wavesContainer">
                        <span
                            v-for="(val, i) in volumeBuffer"
                            :key="i"
                            class="ai-wave-bar"
                            :style="{
                                height: barHeight(val) + 'px',
                            }"
                        />
                    </div>
                </div>
            </template>

            <div v-else class="ai-input-container">
                <el-input
                    ref="promptInput"
                    v-if="configured"
                    v-model="prompt"
                    type="textarea"
                    :disabled="waitingForReply"
                    :autosize="{minRows: 2, maxRows: 6}"
                    :placeholder="$t(`ai.${generationType}.prompt_placeholder`)"
                    @keydown.exact.enter.prevent="submitPrompt"
                    @keydown.exact.ctrl.enter="$event.preventDefault(); prompt += '\n'"
                    class="ai-custom-textarea"
                />
                <template v-else>
                    <div class="el-text keep-whitespace" v-html="$t('ai.flow.enable_instructions.header')" />
                    <div class="mt-2" v-html="highlightedAiConfiguration" />
                    <div class="el-text keep-whitespace" v-html="$t('ai.flow.enable_instructions.footer')" />
                </template>
                <el-text v-if="error" type="danger" size="small" class="error-msg">
                    {{ error }}
                </el-text>
            </div>
        </div>

        <template #footer>
            <div v-if="configured" class="ai-footer">
                <div class="footer-left">
                    <span class="shortcut-hint">(⌘) Ctrl + Alt (⌥) + Shift + K {{ $t("to toggle") }}</span>
                </div>

                <el-select
                    v-if="providers.length > 1"
                    class="w-50 mx-3"
                    :modelValue="selectedProvider"
                    @update:model-value="onProviderChange"
                    :placeholder="$t('ai.flow.select_provider')"
                >
                    <el-option
                        v-for="p in providers"
                        :key="p.id"
                        :label="p.displayName"
                        :value="p.id"
                    />
                </el-select>

                <div class="footer-right">
                    <template v-if="waitingForReply">
                        <span class="generating-label">
                            <el-icon class="is-loading"><Loading /></el-icon>
                            {{ $t(`ai.flow.generating.${generationType}`) }}
                        </span>
                    </template>
                    <template v-else-if="isListening">
                        <el-button
                            class="no-bg-btn"
                            @click="cancelVoice"
                        >
                            <Close />
                        </el-button>
                        <el-button
                            class="no-bg-btn"
                            @click="stopAndValidateVoice"
                        >
                            <Check />
                        </el-button>
                    </template>
                    <template v-else>
                        <el-button
                            class="no-bg-btn"
                            @click="toggleVoiceInput"
                        >
                            <Microphone />
                        </el-button>

                        <el-button
                            type="primary"
                            class="send-btn"
                            :disabled="!prompt.trim()"
                            @click="submitPrompt"
                        >
                            <ArrowUp />
                        </el-button>
                    </template>
                </div>
            </div>
        </template>
    </el-card>
</template>

<script setup lang="ts">
    import {computed, nextTick, onMounted, onUnmounted, ref, watch} from "vue";
    import {Loading} from "@element-plus/icons-vue";
    import Close from "vue-material-design-icons/Close.vue";
    import Check from "vue-material-design-icons/Check.vue";
    import ArrowUp from "vue-material-design-icons/ArrowUp.vue";
    import Microphone from "vue-material-design-icons/Microphone.vue";
    import AiIcon from "./AiIcon.vue";
    import {useAiStore} from "../../stores/ai";
    import {useApiStore} from "../../stores/api";
    import type {InputInstance} from "element-plus";
    import Utils from "../../utils/utils";
    import {useMiscStore} from "override/stores/misc";
    import {aiGenerationTypes, AiGenerationType} from "../../utils/constants";

    const aiStore = useAiStore();
    const apiStore = useApiStore();

    const promptInput = ref<InputInstance>();
    const prompt = ref(sessionStorage.getItem("kestra-ai-prompt") ?? "");
    const initialPromptBeforeListening = ref("");
    const waitingForReply = ref(false);

    const emit = defineEmits<{
        close: [];
        generatedYaml: [string];
    }>();

    watch(prompt, (newValue) => {
        sessionStorage.setItem("kestra-ai-prompt", newValue);
    });

    const props = defineProps<{
        flow: string,
        conversationId: string,
        generationType?: AiGenerationType
    }>();

    const error = ref<string | undefined>(undefined);

    const speechSupported = ref(false);
    const isListening = ref(false);
    const speechRecognition = ref<any | null>(null);
    const basePrompt = ref("");
    const internalWrite = ref(false);

    // Waveform visualizer
    const wavesContainer = ref<HTMLElement | null>(null);
    const BAR_WIDTH = 4; // ~2.5px bar + 1.5px gap
    const volumeBuffer = ref<number[]>([]);
    let barCount = 0;
    let audioContext: AudioContext | null = null;
    let analyser: AnalyserNode | null = null;
    let animationFrame: number | null = null;
    let stream: MediaStream | null = null;

    const MIN_BAR_H = 2;
    const MAX_BAR_H = 28;

    function barHeight(val: number): number {
        // Silent or near-silent: render as a tiny 2px dot (dotted baseline look)
        if (val < 8) return MIN_BAR_H;
        // Active speech: scale quadratically for punchy dynamics
        const n = (val - 8) / 247; // normalize 8-255 to 0-1
        return MIN_BAR_H + n * n * (MAX_BAR_H - MIN_BAR_H);
    }

    const miscStore = useMiscStore();
    const configured = computed(() => miscStore.configs?.isAiEnabled);
    const highlightedAiConfiguration = ref<string | undefined>();

    const providers = ref<{id: string, displayName: string}[]>([]);
    const selectedProvider = ref<string | undefined>(undefined);

    async function fetchProviders() {
        try {
            const list = await aiStore.fetchProviders();
            providers.value = list ?? [];
            if (providers.value.length > 0) {
                selectedProvider.value = providers.value[0].id;
            }
        } catch (e: any) {
            error.value = e.response?.data?.message as string ?? e;
        }
    }

    function onProviderChange(value: string) {
        selectedProvider.value = value;
    }

    function focusPrompt() {
        nextTick(() => {
            promptInput.value?.focus?.();
        });
    }

    async function startAudioAnalysis() {
        try {
            // Compute how many bars fit in the container
            await nextTick();
            const containerWidth = wavesContainer.value?.clientWidth ?? 600;
            barCount = Math.floor(containerWidth / BAR_WIDTH);
            volumeBuffer.value = new Array(barCount).fill(0);

            stream = await navigator.mediaDevices.getUserMedia({audio: true});
            audioContext = new (window.AudioContext || (window as any).webkitAudioContext)();
            analyser = audioContext.createAnalyser();
            const source = audioContext.createMediaStreamSource(stream);

            analyser.fftSize = 256;
            analyser.smoothingTimeConstant = 0.3;
            source.connect(analyser);

            const dataArray = new Uint8Array(analyser.frequencyBinCount);
            let lastPush = 0;
            const PUSH_INTERVAL = 50; // ms between new bars — controls scroll speed
            let peakSincePush = 0;

            const update = (now: number) => {
                if (!analyser) return;
                analyser.getByteFrequencyData(dataArray);

                // Sample several speech-relevant frequency bins and take the max
                const sample = Math.max(
                    dataArray[1] ?? 0,
                    dataArray[3] ?? 0,
                    dataArray[5] ?? 0,
                    dataArray[8] ?? 0,
                    dataArray[12] ?? 0,
                );

                // Track peak between pushes so we don't miss transients
                peakSincePush = Math.max(peakSincePush, sample);

                // Only push a new bar every PUSH_INTERVAL ms
                if (now - lastPush >= PUSH_INTERVAL) {
                    const buf = volumeBuffer.value;
                    buf.push(peakSincePush);
                    if (buf.length > barCount) {
                        buf.shift();
                    }
                    peakSincePush = 0;
                    lastPush = now;
                }

                animationFrame = requestAnimationFrame(update);
            };
            animationFrame = requestAnimationFrame(update);
        } catch (err) {
            console.error("Audio analysis failed", err);
        }
    }

    function stopAudioAnalysis() {
        if (animationFrame) cancelAnimationFrame(animationFrame);
        if (stream) stream.getTracks().forEach(track => track.stop());
        if (audioContext) audioContext.close();
        volumeBuffer.value = [];
        barCount = 0;
    }

    async function submitPrompt() {
        if (!prompt.value.trim()) return;
        error.value = undefined;
        waitingForReply.value = true;
        apiStore.posthogEvents({
            type: "AI_COPILOT",
            action: "prompt_submit",
            ai_copilot_configured: configured.value === true,
        });
        let aiResponse;
        try {
            const type = props.generationType ?? aiGenerationTypes.FLOW;
            aiResponse = await aiStore.generate({
                userPrompt: prompt.value,
                yaml: props.flow,
                conversationId: props.conversationId,
                providerId: selectedProvider.value,
                type: type
            }) as string;
            emit("generatedYaml", aiResponse);
        } catch (e: any) {
            error.value = e.response?.data?.message ?? e.message;
        } finally {
            waitingForReply.value = false;
        }
    }

    function stopAndValidateVoice() {
        speechRecognition.value?.stop();
        isListening.value = false;
        stopAudioAnalysis();
        focusPrompt();
    }

    function cancelVoice() {
        speechRecognition.value?.abort();
        isListening.value = false;
        stopAudioAnalysis();
        internalWrite.value = true;
        prompt.value = initialPromptBeforeListening.value;
        nextTick(() => (internalWrite.value = false));
        focusPrompt();
    }

    function toggleVoiceInput() {
        if (isListening.value) {
            stopAndValidateVoice();
        } else {
            initialPromptBeforeListening.value = prompt.value;
            basePrompt.value = prompt.value.trim();
            isListening.value = true;
            volumeBuffer.value = []; // Reset on click
            startRecognitionSafely();
            startAudioAnalysis();
        }
    }

    function startRecognitionSafely() {
        try {
            speechRecognition.value?.abort();
        } catch {
            // intentionally empty: abort may throw if recognition is not started
        }
        setTimeout(() => {
            try {
                speechRecognition.value?.start();
            } catch {
                isListening.value = false;
                stopAudioAnalysis();
            }
        }, 100);
    }

    onMounted(() => {
        const SR = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
        if (SR) {
            speechSupported.value = true;
            const rec = new SR();
            rec.continuous = true;
            rec.interimResults = true;

            rec.onresult = (event: any) => {
                let interim = "";
                for (let i = event.resultIndex; i < event.results.length; i++) {
                    const res = event.results[i];
                    if (res.isFinal) basePrompt.value += (basePrompt.value ? " " : "") + res[0].transcript;
                    else interim = res[0].transcript;
                }
                internalWrite.value = true;
                prompt.value = (basePrompt.value + (interim ? " " + interim : "")).trim();
                nextTick(() => (internalWrite.value = false));
            };

            rec.onend = () => {
                if (isListening.value) startRecognitionSafely();
            };
            speechRecognition.value = rec;
        }
        focusPrompt();
    });

    onMounted(async () => {
        await fetchProviders();

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
            });
            highlightedAiConfiguration.value = highlighter.codeToHtml("kestra:\n  ai:\n    type: \"gemini\"...", {
                lang: "yaml",
                theme: Utils.getTheme() === "dark" ? "github-dark" : "github-light"
            });
        }
    });

    onUnmounted(() => {
        stopAudioAnalysis();
    });

    watch(prompt, (v) => sessionStorage.setItem("kestra-ai-prompt", v));
</script>

<style scoped lang="scss">
.ai-copilot-card {
    background: var(--ks-background-panel);
    border: 1px solid var(--ks-border-secondary);

    :deep(.el-card__header) {
        padding: 10px 16px;
        border-bottom: none;
    }

    :deep(.el-card__body) {
        padding: 0;
    }

    :deep(.el-card__footer) {
        padding: 8px 16px;
        border-top: none;
    }
}

.ai-body {
    padding: 16px;
    min-height: 80px;
    display: flex;
    flex-direction: column;
    align-items: stretch;
}

/* Voice waveform container — matches the textarea look */
.ai-voice-pill {
    width: 100%;
    display: flex;
    align-items: center;
    background: var(--el-input-bg-color, var(--el-fill-color-blank));
    border: 1px solid var(--el-input-border-color, var(--el-border-color));
    border-radius: var(--el-input-border-radius, var(--el-border-radius-base));
    padding: 8px 12px;
    min-height: 58px;
}

/* Waveform track fills remaining space inside the pill */
.ai-waves-track {
    flex: 1;
    height: 32px;
    display: flex;
    align-items: center;
    gap: 1.5px;
    overflow: hidden;

    .ai-wave-bar {
        flex: 1 1 0;
        min-width: 1.5px;
        max-width: 2.5px;
        min-height: 2px;
        background: var(--ks-content-secondary);
        border-radius: 1px;
    }
}

.ai-input-container {
    width: 100%;

    .error-msg {
        display: block;
        text-align: right;
        margin-top: 8px;
    }
}

.ai-custom-textarea {
    :deep(.el-textarea__inner) {
        color: var(--ks-content-primary) !important;
        font-size: 14px;
        line-height: 1.6;
        resize: none;

        &::placeholder {
            color: var(--ks-content-tertiary);
            font-style: italic;
        }
    }
}

.ai-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.footer-right {
    display: flex;
    gap: 8px;
    align-items: center;
}

.no-bg-btn {
    background: transparent !important;
    border: none !important;
    color: var(--ks-content-tertiary) !important;
    padding: 4px !important;
    font-size: 20px;

    &:hover {
        color: var(--ks-content-primary) !important;
    }
}

.ai-close-button {
    background: transparent !important;
    border: none !important;
    color: var(--ks-content-tertiary) !important;
    padding: 0;

    &:hover {
        color: var(--ks-content-primary) !important;
    }
}

.send-btn {
    background: var(--ks-button-background-primary) !important;
    border: none !important;
    width: 32px !important;
    height: 32px !important;
    border-radius: 6px !important;
    color: var(--ks-content-primary) !important;
    padding: 0 !important;

    &:disabled {
        background: var(--ks-background-card) !important;
        color: var(--ks-content-inactive) !important;
    }
}

.shortcut-hint {
    font-size: 11px;
    color: var(--ks-content-tertiary);
}
</style>
