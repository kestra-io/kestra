<template>
    <el-card
        shadow="never"
        :class="{
            'ai-copilot-card': !props.onboarding,
            'ai-copilot-onboarding-card': props.onboarding,
        }"
    >
        <template #header v-if="!props.onboarding">
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

        <div v-if="props.onboarding" class="ai-body ai-body-onboarding">
            <div class="ai-onboarding-hero">
                <div class="ai-onboarding-icon">
                    <img
                        src="/favicon-192x192.png"
                        alt="Kestra"
                        class="ai-onboarding-logo"
                    >
                </div>
                <h2 class="ai-onboarding-title">
                    {{ $t("welcome_copilot.title") }}
                </h2>
            </div>

            <div class="ai-onboarding-composer-wrap">
                <div v-if="apiFeedback" class="ai-onboarding-info" :role="error ? 'alert' : 'status'">
                    <span class="ai-onboarding-info-content">
                        <el-icon class="ai-onboarding-info-icon">
                            <AlertBox v-if="error" />
                            <InformationOutline v-else />
                        </el-icon>
                        <span>{{ error ?? $t("welcome_copilot.remaining_quota", {count: remainingQuota}) }}</span>
                    </span>
                </div>

                <div class="ai-onboarding-composer" :class="{'api-feedback': apiFeedback}">
                    <template v-if="isListening">
                        <div class="ai-voice-pill ai-voice-pill-onboarding">
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

                    <div v-else class="ai-input-container ai-input-container-onboarding">
                        <el-input
                            ref="promptInput"
                            v-if="configured || props.onboarding"
                            v-model="prompt"
                            type="textarea"
                            :disabled="!props.onboarding && waitingForReply"
                            :readonly="props.onboarding && waitingForReply"
                            :autosize="{minRows: 4, maxRows: 8}"
                            :placeholder="$t('welcome_copilot.placeholder_prompt')"
                            @keydown.exact.enter.prevent="submitPrompt"
                            @keydown.exact.ctrl.enter="$event.preventDefault(); prompt += '\n'"
                            class="ai-custom-textarea ai-custom-textarea-onboarding"
                        />
                        <template v-else>
                            <div class="el-text keep-whitespace" v-html="$t('ai.flow.enable_instructions.header')" />
                            <div class="mt-2" v-html="highlightedAiConfiguration" />
                            <div class="el-text keep-whitespace" v-html="$t('ai.flow.enable_instructions.footer')" />
                        </template>
                        <el-text v-if="error && !props.onboarding" type="danger" size="small" class="error-msg">
                            {{ error }}
                        </el-text>
                    </div>

                    <div v-if="configured" class="ai-footer ai-footer-onboarding">
                        <el-select
                            v-if="providers.length > 1"
                            class="ai-provider-select"
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
                                <template v-if="props.onboarding">
                                    <el-button
                                        type="primary"
                                        class="send-btn send-btn-onboarding"
                                        disabled
                                    >
                                        <el-icon class="is-loading">
                                            <Loading />
                                        </el-icon>
                                    </el-button>
                                </template>
                                <template v-else>
                                    <span class="generating-label">
                                        <el-icon class="is-loading"><Loading /></el-icon>
                                        {{ $t(`ai.flow.generating.${generationType}`) }}
                                    </span>
                                </template>
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
                                    class="send-btn send-btn-onboarding"
                                    :disabled="!prompt.trim()"
                                    @click="submitPrompt"
                                >
                                    <ArrowUp />
                                </el-button>
                            </template>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <div v-else class="ai-body">
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

        <template #footer v-if="!props.onboarding">
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
    import AlertBox from "vue-material-design-icons/AlertBox.vue";
    import InformationOutline from "vue-material-design-icons/InformationOutline.vue";
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
    const initialPromptBeforeListening = ref("");
    const waitingForReply = ref(false);

    const emit = defineEmits<{
        close: [];
        generatedYaml: [string];
        createFlowDirectly: [string];
        onboardingPromptDiverged: [];
    }>();

    const props = defineProps<{
        flow: string,
        conversationId: string,
        generationType?: AiGenerationType,
        namespace?: string,
        onboarding?: boolean,
        initialPrompt?: string,
        onboardingExamples?: {prompt: string; flow: string}[],
        redirectOnUnchangedPrompt?: boolean,
        selectedFromTag?: boolean,
    }>();

    const prompt = ref(
        props.onboarding ? props.initialPrompt ?? "" : sessionStorage.getItem("kestra-ai-prompt") ?? "",
    );

    const error = ref<string | undefined>(undefined);

    const QUOTA_STORAGE_KEY = "kestra-ai-remaining-quota";
    const QUOTA_DATE_KEY = "kestra-ai-remaining-quota-date";
    const todayUTC = new Date().toISOString().slice(0, 10);

    function loadStoredQuota(): string | undefined {
        const date = sessionStorage.getItem(QUOTA_DATE_KEY);
        if (date !== todayUTC) {
            sessionStorage.removeItem(QUOTA_STORAGE_KEY);
            sessionStorage.removeItem(QUOTA_DATE_KEY);
            return undefined;
        }
        return sessionStorage.getItem(QUOTA_STORAGE_KEY) ?? undefined;
    }

    const remainingQuota = ref<string | undefined>(loadStoredQuota());

    function setRemainingQuota(value: string | undefined) {
        remainingQuota.value = value;
        if (value != null) {
            sessionStorage.setItem(QUOTA_STORAGE_KEY, value);
            sessionStorage.setItem(QUOTA_DATE_KEY, todayUTC);
        } else {
            sessionStorage.removeItem(QUOTA_STORAGE_KEY);
            sessionStorage.removeItem(QUOTA_DATE_KEY);
        }
    }

    const apiFeedback = computed(() => !!error.value || (remainingQuota.value != null && props.onboarding));
    const onboardingPromptEdited = ref(false);

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
    const effectiveFlowYaml = computed(() => {
        if (!props.onboarding) {
            return props.flow;
        }

        const normalizedPrompt = prompt.value.trim();
        if (!normalizedPrompt) {
            return undefined;
        }

        const matchedExample = props.onboardingExamples?.find(
            (example) => example.prompt.trim() === normalizedPrompt,
        );

        return matchedExample?.flow;
    });

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
        // Blur before disabling to avoid the textarea focus ring flashing on submit.
        const activeElement = document.activeElement as HTMLElement | null;
        activeElement?.blur?.();

        if (
            props.onboarding &&
            props.selectedFromTag &&
            props.redirectOnUnchangedPrompt &&
            !onboardingPromptEdited.value
        ) {
            waitingForReply.value = true;
            await nextTick();
            emit("createFlowDirectly", props.flow);
            return;
        }

        waitingForReply.value = true;
        apiStore.posthogEvents({
            type: "AI_COPILOT",
            action: "prompt_submit",
            ai_copilot_configured: configured.value === true,
        });
        let aiResult;
        try {
            const type = props.generationType ?? aiGenerationTypes.FLOW;
            if (type === aiGenerationTypes.FLOW) {
                aiResult = await aiStore.generateFlow({
                    userPrompt: prompt.value,
                    conversationId: props.conversationId,
                    providerId: selectedProvider.value,
                    namespace: props.namespace,
                    type: type,
                    ...(effectiveFlowYaml.value ? {yaml: effectiveFlowYaml.value} : {}),
                });
            } else {
                aiResult = await aiStore.generate({
                    userPrompt: prompt.value,
                    conversationId: props.conversationId,
                    providerId: selectedProvider.value,
                    type: type,
                    ...(effectiveFlowYaml.value ? {yaml: effectiveFlowYaml.value} : {}),
                });
            }
            setRemainingQuota(aiResult.remainingQuota);
            emit("generatedYaml", aiResult.data);
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

    watch(
        () => [props.onboarding, props.initialPrompt] as const,
        ([onboarding, initialPrompt]) => {
            if (onboarding) {
                prompt.value = initialPrompt ?? "";
                onboardingPromptEdited.value = false;
            }
        },
        {immediate: true},
    );

    watch(
        prompt,
        (value) => {
            if (!props.onboarding) {
                sessionStorage.setItem("kestra-ai-prompt", value);
            }

            if (props.onboarding) {
                const hasDiverged = value.trim() !== (props.initialPrompt ?? "").trim();
                if (!onboardingPromptEdited.value && hasDiverged) {
                    emit("onboardingPromptDiverged");
                }
                onboardingPromptEdited.value = hasDiverged;
            }
        },
    );
</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/_variables.scss";

.ai-copilot-onboarding-card {
    border: none;
    background: transparent;
    overflow: visible;

    :deep(.el-card__body) {
        padding: 0;
        overflow: visible;
    }
}

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

.ai-body-onboarding {
    align-items: center;
    gap: 32px;
    padding: 40px 24px 10px;
}

.ai-onboarding-hero {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 22px;
    text-align: center;
}

.ai-onboarding-icon {
    width: 69px;
    height: 69px;
    display: grid;
    place-items: center;
}

.ai-onboarding-logo {
    width: 69px;
    height: 69px;
    object-fit: contain;
    border-radius: 20px;
}

.ai-onboarding-title {
    margin: 0;
    max-width: 760px;
    color: var(--ks-content-primary);
    font-size: $font-size-2xl;
    line-height: 1.08;
    font-weight: 600;
    margin-bottom: 3rem;
}

.ai-onboarding-info {
    display: flex;
    align-items: center;
    margin: 0 16px -1px;
    padding: 8px 14px;
    border: 1px solid var(--ks-border-info);
    border-bottom: 0;
    border-radius: 12px 12px 0 0;
    background: var(--ks-background-info);
    color: var(--ks-content-info);
    box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
}

.ai-onboarding-info-content {
    display: inline-flex;
    align-items: flex-start;
    gap: 8px;
    width: 100%;
    font-size: $font-size-sm;
    line-height: 1.35;
    white-space: normal;
}

.ai-onboarding-info-icon {
    color: var(--ks-content-info);
    font-size: 16px;
    flex-shrink: 0;
    align-self: center;
}

.ai-onboarding-composer-wrap {
    display: flex;
    flex-direction: column;
    width: 100%;
    max-width: 1120px;
}

.ai-onboarding-composer {
    width: 100%;
    height: 152px;
    border: 1px solid transparent;
    border-radius: 20px;
    background: var(--ks-background-input);
    box-shadow:
        0 8px 20px rgba(15, 23, 42, 0.035),
        0 22px 44px rgba(15, 23, 42, 0.05);
    display: flex;
    flex-direction: column;
    overflow: hidden;
    outline: none !important;
}

.ai-onboarding-composer.api-feedback {
    border-color: var(--ks-border-info);
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

.ai-voice-pill-onboarding {
    flex: 1;
    min-height: 0;
    justify-content: center;
    padding: 12px 24px;
    border: none;
    border-radius: 0;
    background: transparent;
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

.ai-voice-pill-onboarding .ai-waves-track {
    max-width: calc(100% - 12px);
}

.ai-input-container {
    width: 100%;

    .error-msg {
        display: block;
        text-align: right;
        margin-top: 8px;
    }
}

.ai-input-container-onboarding {
    flex: 1;
    min-height: 0;
    padding: 14px 18px 0;
    display: flex;
    flex-direction: column;
    outline: none !important;
    box-shadow: none !important;
    border: none !important;
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

.ai-custom-textarea-onboarding {
    flex: 1;
    --el-disabled-bg-color: transparent;
    --el-disabled-text-color: var(--ks-content-primary);
    --el-fill-color-light: transparent;
    --el-fill-color-blank: transparent;
    --el-input-border-color: transparent;
    --el-input-hover-border-color: transparent;
    --el-input-focus-border-color: transparent;
    --el-border-color: transparent;
    --el-input-focus-border: transparent;
    --el-input-box-shadow: none;

    :deep(.el-textarea) {
        height: 100%;
        box-shadow: none !important;
        outline: none !important;
        border: none !important;
        background: transparent !important;
    }

    :deep(.el-textarea:focus-within) {
        box-shadow: none !important;
        outline: none !important;
        border: none !important;
        background: transparent !important;
    }

    :deep(.el-textarea.is-disabled) {
        box-shadow: none !important;
        outline: none !important;
        border: none !important;
        background: transparent !important;
    }

    :deep(.el-textarea__inner) {
        min-height: 100% !important;
        height: 100% !important;
        padding: 16px 14px 8px;
        border: none !important;
        border-radius: 0;
        background: transparent !important;
        outline: none !important;
        box-shadow: none !important;
        font-size: $font-size-md;
        line-height: 1.45;

        &::placeholder {
            font-style: normal;
            font-size: $font-size-md;
        }

        &:disabled {
            background: transparent !important;
            color: var(--ks-content-primary) !important;
            -webkit-text-fill-color: var(--ks-content-primary) !important;
            opacity: 1;
            cursor: default;
        }

        &:focus,
        &:focus-visible,
        &:active {
            border: none !important;
            outline: none !important;
            box-shadow: none !important;
        }
    }

    :deep(.el-textarea.is-disabled .el-textarea__inner) {
        background: transparent !important;
        background-color: transparent !important;
        color: var(--ks-content-primary) !important;
        -webkit-text-fill-color: var(--ks-content-primary) !important;
        opacity: 1;
        box-shadow: none !important;
        border: none !important;
    }

    :deep(.el-textarea__inner:hover) {
        box-shadow: none !important;
        border: none !important;
        outline: none !important;
    }

    :deep(.el-textarea__inner::-webkit-focus-inner) {
        border: 0;
    }
}

.ai-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.ai-footer-onboarding {
    justify-content: flex-end;
    gap: 8px;
    padding: 2px 12px 8px;
    margin-top: -2px;
}

.ai-provider-select {
    width: min(240px, 100%);
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
    color: var(--ks-button-content-primary) !important;
    padding: 0 !important;

    &:disabled {
        background: var(--ks-background-card) !important;
        color: var(--ks-content-inactive) !important;
    }
}

.send-btn-onboarding {
    width: 42px !important;
    height: 42px !important;
    border-radius: 999px !important;
    color: white !important;
    margin-left: calc(1rem / 2) !important;

    &:hover,
    &:focus-visible {
        color: white !important;
    }

    &:disabled {
        color: var(--ks-content-inactive) !important;
    }
}

.shortcut-hint {
    font-size: 11px;
    color: var(--ks-content-tertiary);
}

@media (max-width: 768px) {
    .ai-body-onboarding {
        gap: 24px;
        padding: 24px 12px 16px;
    }

    .ai-onboarding-icon {
        width: 69px;
        height: 69px;
    }

    .ai-onboarding-logo {
        width: 69px;
        height: 69px;
        border-radius: 22px;
    }

    .ai-onboarding-composer {
        border-radius: 18px;
    }

    .ai-onboarding-info {
        margin: 0 12px -1px;
    }

    .ai-custom-textarea-onboarding {
        :deep(.el-textarea__inner) {
            font-size: $font-size-md;

            &::placeholder {
                font-size: $font-size-md;
            }
        }
    }

    .ai-footer-onboarding {
        flex-wrap: wrap;
    }

    .ai-provider-select {
        width: 100%;
        order: 3;
    }

    .footer-left {
        width: 100%;
    }

    .footer-right {
        margin-left: auto;
    }
}
</style>
