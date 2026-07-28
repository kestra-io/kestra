<template>
    <div class="copilot-composer">
        <!-- While dictating, the live waveform replaces the textarea. -->
        <div v-if="isListening" ref="wavesContainer" class="copilot-voice" data-test="copilot-voice">
            <span
                v-for="(value, index) in volumeBuffer"
                :key="index"
                class="copilot-wave-bar"
                :style="{height: `${barHeight(value)}px`}"
            />
        </div>
        <!--
            Native borderless textarea inside the bordered wrapper so the composer reads as a
            single box (Figma). A wrapped design-system text input can't be made borderless
            without overriding its inner inset-shadow border, which the design-system rules forbid.
        -->
        <textarea
            v-else
            ref="textareaEl"
            v-model="draft"
            class="copilot-textarea"
            :rows="rows ?? 1"
            :placeholder="placeholder ?? $t('ai.copilot.placeholder')"
            :disabled="disabled"
            data-test="copilot-composer-input"
            @keydown="onKeydown"
            @input="autosize"
        />

        <div class="copilot-composer-actions">
            <KsDropdown trigger="click" data-test="copilot-mode-selector">
                <KsButton size="small" class="copilot-mode-trigger">
                    <span class="copilot-mode-item">
                        <component :is="currentMode?.icon" :size="16" />
                        {{ currentMode?.label }}
                        <ChevronDown class="copilot-mode-chevron" />
                    </span>
                </KsButton>
                <template #dropdown>
                    <KsDropdownMenu>
                        <KsDropdownItem
                            v-for="option in modeOptions"
                            :key="option.value"
                            :class="{'copilot-mode-item--active': option.value === mode}"
                            @click="emit('update:mode', option.value)"
                        >
                            <span class="copilot-mode-item">
                                <component :is="option.icon" :size="16" />
                                {{ option.label }}
                            </span>
                        </KsDropdownItem>
                    </KsDropdownMenu>
                </template>
            </KsDropdown>

            <div class="copilot-composer-right">
                <template v-if="isListening">
                    <KsButton
                        text
                        :icon="Close"
                        :aria-label="$t('ai.copilot.voice.cancel')"
                        data-test="copilot-voice-cancel"
                        @click="cancelVoice"
                    />
                    <KsButton
                        circle
                        type="primary"
                        :icon="Check"
                        :aria-label="$t('ai.copilot.voice.stop')"
                        data-test="copilot-voice-confirm"
                        @click="stopAndValidateVoice"
                    />
                </template>
                <template v-else>
                    <KsDropdown v-if="providers?.length" trigger="click" data-test="copilot-provider-selector">
                        <KsButton size="small" text class="copilot-provider-trigger">
                            {{ currentProviderLabel }}
                            <ChevronDown class="copilot-mode-chevron" />
                        </KsButton>
                        <template #dropdown>
                            <KsDropdownMenu>
                                <KsDropdownItem
                                    v-for="p in providers"
                                    :key="p.id"
                                    :class="{'copilot-mode-item--active': p.id === provider}"
                                    @click="p.id && emit('update:provider', p.id)"
                                >
                                    {{ p.displayName }}
                                </KsDropdownItem>
                            </KsDropdownMenu>
                        </template>
                    </KsDropdown>
                    <KsButton
                        v-if="speechSupported"
                        text
                        :icon="Microphone"
                        :disabled="disabled"
                        :aria-label="$t('ai.copilot.voice.start')"
                        data-test="copilot-mic"
                        @click="toggleVoiceInput"
                    />
                    <KsButton
                        circle
                        type="primary"
                        :icon="ArrowUp"
                        :disabled="!canSubmit"
                        :aria-label="$t('ai.copilot.send')"
                        data-test="copilot-send"
                        @click="submit"
                    />
                </template>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, nextTick, watch, onMounted, onBeforeUnmount, type Component} from "vue"
    import {useI18n} from "vue-i18n"
    import ArrowUp from "vue-material-design-icons/ArrowUp.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import Microphone from "vue-material-design-icons/Microphone.vue"
    import Check from "vue-material-design-icons/Check.vue"
    import Close from "vue-material-design-icons/Close.vue"
    // Per-mode icons taken from the UI-2.0 Figma: Edit → wrench, Plan → map.
    // Ask isn't shown in that file, so we use a Q&A chat icon to match its read-only intent.
    import ChatQuestionOutline from "vue-material-design-icons/ChatQuestionOutline.vue"
    import Wrench from "vue-material-design-icons/Wrench.vue"
    import MapOutline from "vue-material-design-icons/MapOutline.vue"
    import type {Mode} from "./types"

    const props = defineProps<{
        mode: Mode
        /** Disables input while a turn is streaming or awaiting confirmation. */
        disabled?: boolean
        /** Overrides the placeholder (e.g. the descriptive helper text in the empty state). */
        placeholder?: string
        /** Initial visible rows (empty state uses more so the helper text wraps); collapses on input. */
        rows?: number
        /** Available AI providers; the selector is hidden when none are known. */
        providers?: {id?: string; displayName?: string}[]
        /** Currently selected provider id (v-model:provider). */
        provider?: string
    }>()

    const emit = defineEmits<{
        (e: "submit", prompt: string): void
        (e: "update:mode", mode: Mode): void
        (e: "update:provider", provider: string): void
    }>()

    const {t} = useI18n()

    // The composer text. A v-model so a parent can seed it (e.g. "Fix with AI" prefills a prompt).
    const draft = defineModel<string>({default: ""})
    const textareaEl = ref<HTMLTextAreaElement>()

    // Values are the backend Mode enum; icons match the Figma mode pills. Ordered by increasing
    // capability, matching the backend's cumulative tool families (Ask ⊂ Plan ⊂ Edit).
    const modeOptions = computed<{label: string; value: Mode; icon: Component}[]>(() => [
        {label: t("ai.copilot.mode.ask"), value: "ASK", icon: ChatQuestionOutline},
        {label: t("ai.copilot.mode.plan"), value: "PLAN", icon: MapOutline},
        {label: t("ai.copilot.mode.edit"), value: "EDIT", icon: Wrench},
    ])

    const currentMode = computed(() => modeOptions.value.find((o) => o.value === props.mode))

    const currentProviderLabel = computed(() => {
        const list = props.providers ?? []
        return (list.find((p) => p.id === props.provider) ?? list[0])?.displayName
    })

    const canSubmit = computed(() => !props.disabled && draft.value.trim().length > 0)

    // Grow the textarea with its content, up to the CSS max-height (then it scrolls).
    function autosize(): void {
        const el = textareaEl.value
        if (!el) return
        el.style.height = "auto"
        el.style.height = `${el.scrollHeight}px`
    }

    function submit(): void {
        if (!canSubmit.value) return
        emit("submit", draft.value.trim())
        draft.value = ""
    }

    /** Focus the input — used to hand control back to the user after "Reply to revise". */
    function focus(): void {
        textareaEl.value?.focus()
    }

    defineExpose({focus})

    // Keep the height in sync when the draft is cleared (e.g. after submit).
    watch(draft, () => nextTick(autosize))

    // Enter submits; Shift+Enter inserts a newline.
    function onKeydown(event: KeyboardEvent): void {
        if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault()
            submit()
        }
    }

    /* ── Voice input (Web Speech API + Web Audio waveform), ported from AiCopilot ── */
    const speechSupported = ref(false)
    const isListening = ref(false)
    // Transcript captured before this dictation started, so interim results append cleanly.
    const baseDraft = ref("")
    const draftBeforeListening = ref("")
    let recognition: any = null

    // Waveform visualizer.
    const wavesContainer = ref<HTMLElement | null>(null)
    const volumeBuffer = ref<number[]>([])
    const BAR_WIDTH = 4
    const MIN_BAR_H = 2
    const MAX_BAR_H = 28
    let barCount = 0
    let audioContext: AudioContext | null = null
    let analyser: AnalyserNode | null = null
    let animationFrame: number | null = null
    let stream: MediaStream | null = null

    function barHeight(value: number): number {
        if (value < 8) return MIN_BAR_H
        const n = (value - 8) / 247
        return MIN_BAR_H + n * n * (MAX_BAR_H - MIN_BAR_H)
    }

    async function startAudioAnalysis(): Promise<void> {
        try {
            await nextTick()
            barCount = Math.floor((wavesContainer.value?.clientWidth ?? 600) / BAR_WIDTH)
            volumeBuffer.value = Array(barCount).fill(0)

            stream = await navigator.mediaDevices.getUserMedia({audio: true})
            audioContext = new (window.AudioContext || (window as any).webkitAudioContext)()
            analyser = audioContext.createAnalyser()
            analyser.fftSize = 256
            analyser.smoothingTimeConstant = 0.3
            audioContext.createMediaStreamSource(stream).connect(analyser)

            const data = new Uint8Array(analyser.frequencyBinCount)
            let lastPush = 0
            let peak = 0
            const PUSH_INTERVAL = 50

            const update = (now: number) => {
                if (!analyser) return
                analyser.getByteFrequencyData(data)
                peak = Math.max(peak, data[1] ?? 0, data[3] ?? 0, data[5] ?? 0, data[8] ?? 0, data[12] ?? 0)
                if (now - lastPush >= PUSH_INTERVAL) {
                    const buf = volumeBuffer.value
                    buf.push(peak)
                    if (buf.length > barCount) buf.shift()
                    peak = 0
                    lastPush = now
                }
                animationFrame = requestAnimationFrame(update)
            }
            animationFrame = requestAnimationFrame(update)
        } catch (error) {
            // Mic permission denied / unavailable — drop out of listening gracefully.
            console.error("Audio analysis failed", error)
            isListening.value = false
        }
    }

    function stopAudioAnalysis(): void {
        if (animationFrame) cancelAnimationFrame(animationFrame)
        stream?.getTracks().forEach((track) => track.stop())
        audioContext?.close()
        animationFrame = null
        stream = null
        audioContext = null
        analyser = null
        volumeBuffer.value = []
        barCount = 0
    }

    function startRecognitionSafely(): void {
        try {
            recognition?.abort()
        } catch {
            // abort throws if not started — ignore.
        }
        setTimeout(() => {
            try {
                recognition?.start()
            } catch {
                isListening.value = false
                stopAudioAnalysis()
            }
        }, 100)
    }

    function toggleVoiceInput(): void {
        if (isListening.value) {
            stopAndValidateVoice()
            return
        }
        draftBeforeListening.value = draft.value
        baseDraft.value = draft.value.trim()
        isListening.value = true
        volumeBuffer.value = []
        startRecognitionSafely()
        startAudioAnalysis()
    }

    function stopAndValidateVoice(): void {
        recognition?.stop()
        isListening.value = false
        stopAudioAnalysis()
        nextTick(() => textareaEl.value?.focus())
    }

    function cancelVoice(): void {
        recognition?.abort()
        isListening.value = false
        stopAudioAnalysis()
        draft.value = draftBeforeListening.value
    }

    // If the composer gets disabled mid-dictation (turn started), bail out of voice.
    watch(() => props.disabled, (value) => {
        if (value && isListening.value) cancelVoice()
    })

    onMounted(() => {
        const SR = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition
        if (!SR) return
        speechSupported.value = true
        recognition = new SR()
        recognition.continuous = true
        recognition.interimResults = true
        recognition.onresult = (event: any) => {
            let interim = ""
            for (let i = event.resultIndex; i < event.results.length; i++) {
                const result = event.results[i]
                if (result.isFinal) baseDraft.value += (baseDraft.value ? " " : "") + result[0].transcript
                else interim = result[0].transcript
            }
            draft.value = (baseDraft.value + (interim ? ` ${interim}` : "")).trim()
        }
        recognition.onend = () => {
            if (isListening.value) startRecognitionSafely()
        }
    })

    onBeforeUnmount(() => {
        try {
            recognition?.abort()
        } catch {
            // ignore
        }
        stopAudioAnalysis()
    })
</script>

<style scoped>
    .copilot-composer {
        display: flex;
        flex-direction: column;
        width: 100%;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-4);
        border: 1px solid var(--ks-border-strong);
        border-radius: var(--ks-radius-lg);
        background: var(--ks-bg-input);
        box-shadow: var(--ks-shadow-element);
    }

    .copilot-textarea {
        width: 100%;
        max-height: 9rem;
        border: none;
        outline: none;
        resize: none;
        padding: 0;
        background: transparent;
        color: var(--ks-text-primary);
        font: inherit;
        line-height: 1.5;
    }

    .copilot-textarea::placeholder {
        color: var(--ks-text-secondary);
    }

    /* Live dictation waveform — occupies the textarea's slot. */
    .copilot-voice {
        display: flex;
        align-items: center;
        gap: 1.5px;
        height: var(--ks-spacing-8);
        overflow: hidden;
    }

    .copilot-wave-bar {
        flex: 1 1 0;
        min-width: 1.5px;
        max-width: 2.5px;
        min-height: 2px;
        border-radius: 1px;
        background: var(--ks-text-secondary);
    }

    .copilot-composer-actions {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--ks-spacing-2);
    }

    .copilot-composer-right {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-1);
    }

    /* Figma mode control: a subtle bg-tag pill (label + chevron), not a solid button. */
    .copilot-mode-trigger {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        background: var(--ks-bg-tag);
        border: none;
        color: var(--ks-text-primary);
        border-radius: var(--ks-radius-sm);
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
    }

    .copilot-mode-chevron {
        display: inline-flex;
        font-size: 1rem;
        color: var(--ks-text-secondary);
    }

    .copilot-mode-item {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-3);
    }

    /* Provider picker reads as a subdued, secondary control (Figma "gpt-5-nano"). */
    .copilot-provider-trigger {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        color: var(--ks-text-secondary);
    }
</style>
