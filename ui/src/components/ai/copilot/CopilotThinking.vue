<template>
    <!-- Shown while the model is working before its next output arrives. Decorative: the rotating
         flavour words would spam a screen reader, so it's hidden from the a11y tree — the transcript's
         `aria-busy` + the streamed tokens convey "working" instead. -->
    <div class="copilot-thinking" data-test="copilot-thinking" aria-hidden="true">
        <Transition name="copilot-word" mode="out-in">
            <KsText :key="word" size="small" class="copilot-thinking-label">{{ word }}</KsText>
        </Transition><span
            class="copilot-thinking-dots"
            aria-hidden="true"
        />
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted, onBeforeUnmount} from "vue"
    import {useI18n} from "vue-i18n"

    const {tm, rt} = useI18n()

    // Orchestration-flavoured status words that rotate while the agent works, instead of a single
    // static "Thinking". Sourced from i18n (ai.copilot.thinkingWords) so they localise.
    const words = computed(() => (tm("ai.copilot.thinkingWords") as unknown[]).map((entry) => rt(entry as string)))

    const index = ref(0)
    const word = computed(() => words.value[index.value] ?? "")

    /** A random index in [0, count) that is never the current one, so no word repeats back-to-back. */
    function nextRandomIndex(current: number, count: number): number {
        if (count <= 1) return 0
        const pick = Math.floor(Math.random() * (count - 1))
        return pick >= current ? pick + 1 : pick
    }

    // Show the words in random order, advancing every 5s (a random start, then a random next each tick).
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
        align-items: baseline;
        margin-bottom: var(--ks-spacing-3);
        padding: 0 var(--ks-spacing-1);
    }

    .copilot-thinking-label {
        --kel-text-color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
    }

    /* Cross-fade each word as it swaps, so the rotation reads as intentional rather than a flicker. */
    .copilot-word-enter-active,
    .copilot-word-leave-active {
        transition: opacity 0.2s ease;
    }

    .copilot-word-enter-from,
    .copilot-word-leave-to {
        opacity: 0;
    }

    /*
        Animated "rising dots": 1 → 3 then back to 1, on a loop. Driven purely by a CSS
        keyframe animation on the pseudo-element's `content` (discrete steps, no timers to
        clean up). Sits at the end of the line so growing/shrinking never reflows other text.
    */
    .copilot-thinking-dots::after {
        content: ".";
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
        animation: copilot-thinking-dots 1.2s linear infinite;
    }

    @keyframes copilot-thinking-dots {
        0%   { content: "."; }
        33%  { content: ".."; }
        66%  { content: "..."; }
        100% { content: "."; }
    }
</style>
