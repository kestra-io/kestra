<template>
    <!-- The copilot working indicator: an animated Kestra mark plus a rotating flavour word. Shown
         while a turn runs. Decorative: the flavour words would spam a screen reader, so it's hidden
         from the a11y tree — the transcript's `aria-busy` + the streamed tokens convey "working". -->
    <div class="copilot-thinking" data-test="copilot-thinking" aria-hidden="true">
        <CopilotMarkAnimation :phase="phase" />
        <!-- The rotating word only reads during the "thinking" gap (before any output); while the
             answer streams the dots alone carry the state, matching the design. -->
        <Transition v-if="phase === 'thinking'" name="copilot-word" mode="out-in">
            <KsText :key="word" size="small" class="copilot-thinking-label">{{ word }}…</KsText>
        </Transition>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted, onBeforeUnmount} from "vue"
    import {useI18n} from "vue-i18n"
    import CopilotMarkAnimation from "./CopilotMarkAnimation.vue"

    withDefaults(defineProps<{
        /** Which movement the mark plays; follows the turn lifecycle. */
        phase?: "thinking" | "answering" | "end"
    }>(), {phase: "thinking"})

    const {tm, rt} = useI18n()

    const words = computed(() => (tm("ai.copilot.thinkingWords") as unknown[]).map((entry) => rt(entry as string)))

    const index = ref(0)
    const word = computed(() => words.value[index.value] ?? "")

    function nextRandomIndex(current: number, count: number): number {
        if (count <= 1) return 0
        const pick = Math.floor(Math.random() * (count - 1))
        return pick >= current ? pick + 1 : pick
    }

    const ROTATE_MS = 5000
    let timer: ReturnType<typeof setInterval> | undefined
    onMounted(() => {
        const count = words.value.length
        if (count === 0) return
        index.value = Math.floor(Math.random() * count)
        timer = setInterval(() => {
            index.value = nextRandomIndex(index.value, words.value.length)
        }, ROTATE_MS)
    })
    onBeforeUnmount(() => clearInterval(timer))
</script>

<style scoped>
    .copilot-thinking {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        margin-bottom: var(--ks-spacing-3);
        padding: 0 var(--ks-spacing-1);
    }

    .copilot-thinking-label {
        --kel-text-color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
    }

    .copilot-word-enter-active,
    .copilot-word-leave-active {
        transition: opacity 0.2s ease;
    }

    .copilot-word-enter-from,
    .copilot-word-leave-to {
        opacity: 0;
    }
</style>
