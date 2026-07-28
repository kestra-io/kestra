<template>
    <!-- Decorative Kestra-mark animation shown beside the copilot status word while a turn runs.
         Three brand dots that bounce ("thinking"), ripple left→right ("answering"), then gather and
         fade ("end") — a lightweight CSS/SVG reproduction of the delivered Lottie set (no runtime dep).
         Purely decorative: the transcript's aria-busy + the status word already convey "working". -->
    <svg
        class="copilot-mark"
        :class="`copilot-mark-${phase}`"
        viewBox="0 0 44 16"
        width="30"
        height="11"
        fill="none"
        aria-hidden="true"
        focusable="false"
    >
        <!-- Brand mark tints (purple → pink), matching the Kestra logo palette. -->
        <circle class="copilot-mark-dot copilot-mark-dot-1" cx="8" cy="8" r="6" fill="#A950FF" />
        <circle class="copilot-mark-dot copilot-mark-dot-2" cx="22" cy="8" r="6" fill="#CD88FF" />
        <circle class="copilot-mark-dot copilot-mark-dot-3" cx="36" cy="8" r="6" fill="#F62E76" />
    </svg>
</template>

<script setup lang="ts">
    /** Which movement the dots play; driven by the copilot turn lifecycle. */
    defineProps<{phase: "thinking" | "answering" | "end"}>()
</script>

<style scoped>
    .copilot-mark {
        display: inline-block;
        vertical-align: middle;
        flex: none;
    }

    .copilot-mark-dot {
        /* Scale/translate each dot about its own centre. */
        transform-box: fill-box;
        transform-origin: center;
    }

    /* Thinking — staggered vertical bounce, like a typing indicator. */
    .copilot-mark-thinking .copilot-mark-dot {
        animation: copilot-mark-bounce 1.2s ease-in-out infinite;
    }
    .copilot-mark-thinking .copilot-mark-dot-2 { animation-delay: 0.15s; }
    .copilot-mark-thinking .copilot-mark-dot-3 { animation-delay: 0.3s; }

    /* Answering — a left→right ripple of opacity + a gentle pulse while tokens stream. */
    .copilot-mark-answering .copilot-mark-dot {
        animation: copilot-mark-ripple 1.1s ease-in-out infinite;
    }
    .copilot-mark-answering .copilot-mark-dot-2 { animation-delay: 0.18s; }
    .copilot-mark-answering .copilot-mark-dot-3 { animation-delay: 0.36s; }

    /* End — dots gather toward the centre and fade out once as the turn closes. */
    .copilot-mark-end .copilot-mark-dot {
        animation: copilot-mark-gather 0.55s ease-in forwards;
    }
    .copilot-mark-end .copilot-mark-dot-1 { --copilot-mark-gather-x: 14px; }
    .copilot-mark-end .copilot-mark-dot-3 { --copilot-mark-gather-x: -14px; }

    @keyframes copilot-mark-bounce {
        0%, 60%, 100% { transform: translateY(0); }
        30%           { transform: translateY(-4px); }
    }

    @keyframes copilot-mark-ripple {
        0%, 100% { opacity: 0.35; transform: scale(0.82); }
        40%      { opacity: 1;    transform: scale(1); }
    }

    @keyframes copilot-mark-gather {
        to { transform: translateX(var(--copilot-mark-gather-x, 0)); opacity: 0; }
    }

    /* Respect reduced-motion: hold the dots steady rather than animating. */
    @media (prefers-reduced-motion: reduce) {
        .copilot-mark-dot {
            animation: none !important;
            opacity: 1;
            transform: none;
        }
    }
</style>
