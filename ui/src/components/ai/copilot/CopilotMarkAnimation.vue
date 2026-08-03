<template>
    <!-- Decorative Kestra-mark animation shown beside the copilot status word while a turn runs.
         Three brand dots that bounce ("thinking") and ripple left→right ("answering"); when the turn
         ends they gather and bloom into the full Kestra mark ("end"). A lightweight CSS/SVG
         reproduction of the delivered Lottie set (no runtime dep). Purely decorative: the transcript's
         aria-busy + the status word already convey "working", so the whole thing is aria-hidden. -->
    <span class="copilot-mark" :class="`copilot-mark-${phase}`" aria-hidden="true">
        <!-- The three dots (brand tints, purple → pink). -->
        <svg class="copilot-mark-dots" viewBox="0 0 44 16" width="30" height="11" fill="none" focusable="false">
            <circle class="copilot-mark-dot copilot-mark-dot-1" cx="8" cy="8" r="6" fill="#A950FF" />
            <circle class="copilot-mark-dot copilot-mark-dot-2" cx="22" cy="8" r="6" fill="#CD88FF" />
            <circle class="copilot-mark-dot copilot-mark-dot-3" cx="36" cy="8" r="6" fill="#F62E76" />
        </svg>

        <!-- The full Kestra mark, centred over the dots. Hidden until the "end" phase blooms it in. -->
        <svg class="copilot-mark-logo" viewBox="0 0 264 264" width="17" height="17" fill="none" focusable="false">
            <path fill="#A950FF" d="M125.517 110.8C129.097 107.219 134.902 107.219 138.482 110.8L153.199 125.517C156.78 129.097 156.78 134.902 153.199 138.482L138.482 153.2C134.902 156.78 129.097 156.78 125.517 153.2L110.799 138.482C107.219 134.902 107.219 129.097 110.799 125.517L125.517 110.8Z" />
            <path fill="#A950FF" d="M193.302 110.682C196.818 107.166 202.519 107.166 206.035 110.682L220.985 125.632C224.501 129.148 224.501 134.849 220.985 138.365L206.035 153.314C202.519 156.831 196.818 156.831 193.302 153.314L178.353 138.365C174.836 134.849 174.836 129.148 178.353 125.632L193.302 110.682Z" />
            <path fill="#E9C1FF" d="M125.633 43.0151C129.149 39.4989 134.85 39.4989 138.366 43.0151L153.315 57.9643C156.832 61.4806 156.832 67.1816 153.315 70.6979L138.366 85.6471C134.85 89.1633 129.149 89.1633 125.633 85.6471L110.683 70.6979C107.167 67.1816 107.167 61.4806 110.683 57.9643L125.633 43.0151Z" />
            <path fill="#CD88FF" d="M119.368 91.683C122.948 95.2633 122.948 101.068 119.368 104.648L104.651 119.366C101.071 122.946 95.2657 122.946 91.6853 119.366L76.9681 104.648C73.3878 101.068 73.3878 95.2633 76.9681 91.683L91.6854 76.9657C95.2657 73.3854 101.071 73.3854 104.651 76.9657L119.368 91.683Z" />
            <path fill="#A950FF" d="M85.6481 125.632C89.1643 129.148 89.1643 134.849 85.6481 138.365L70.6988 153.314C67.1826 156.831 61.4816 156.831 57.9653 153.314L43.0161 138.365C39.4998 134.849 39.4998 129.148 43.0161 125.632L57.9653 110.682C61.4816 107.166 67.1826 107.166 70.6988 110.682L85.6481 125.632Z" />
            <path fill="#CD88FF" d="M187.035 91.683C190.615 95.2633 190.615 101.068 187.035 104.648L172.317 119.366C168.737 122.946 162.932 122.946 159.352 119.366L144.635 104.648C141.054 101.068 141.054 95.2633 144.635 91.683L159.352 76.9657C162.932 73.3854 168.737 73.3854 172.317 76.9657L187.035 91.683Z" />
            <path fill="#F62E76" d="M146.483 188.339C154.482 196.338 154.482 209.306 146.483 217.305C138.485 225.303 125.516 225.303 117.517 217.305C109.519 209.306 109.519 196.338 117.517 188.339C125.516 180.34 138.485 180.34 146.483 188.339Z" />
        </svg>
    </span>
</template>

<script setup lang="ts">
    /** Which movement the mark plays; driven by the copilot turn lifecycle. */
    defineProps<{phase: "thinking" | "answering" | "end"}>()
</script>

<style scoped>
    .copilot-mark {
        position: relative;
        display: inline-flex;
        align-items: center;
        vertical-align: middle;
        flex: none;
    }

    .copilot-mark-dots {
        display: block;
        /* The dots sit near the top of the viewBox and the "thinking" bounce lifts them past it;
           an SVG's viewport clips by default, so let them overflow to show the full bounce. */
        overflow: visible;
    }

    .copilot-mark-dot {
        /* Scale/translate each dot about its own centre. */
        transform-box: fill-box;
        transform-origin: center;
    }

    /* The full mark sits centred over the dots and stays hidden until the end phase. */
    .copilot-mark-logo {
        position: absolute;
        left: 50%;
        top: 50%;
        transform: translate(-50%, -50%) scale(0.3);
        opacity: 0;
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

    /* End — dots gather toward the centre and fade, then the full mark blooms in. */
    .copilot-mark-end .copilot-mark-dot {
        animation: copilot-mark-gather 0.45s ease-in forwards;
    }
    .copilot-mark-end .copilot-mark-dot-1 { --copilot-mark-gather-x: 14px; }
    .copilot-mark-end .copilot-mark-dot-3 { --copilot-mark-gather-x: -14px; }
    .copilot-mark-end .copilot-mark-logo {
        /* Bloom in (~0.5s), hold the mark for 3s, then fade it away (~0.5s). */
        animation: copilot-mark-bloom 4s ease-out 0.2s forwards;
    }

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

    @keyframes copilot-mark-bloom {
        0%    { opacity: 0; transform: translate(-50%, -50%) scale(0.3); }
        12.5% { opacity: 1; transform: translate(-50%, -50%) scale(1); }
        87.5% { opacity: 1; transform: translate(-50%, -50%) scale(1); }
        100%  { opacity: 0; transform: translate(-50%, -50%) scale(1); }
    }

    /* Respect reduced-motion: hold the dots steady; in the end phase, just show the final mark. */
    @media (prefers-reduced-motion: reduce) {
        .copilot-mark-dot {
            animation: none !important;
            opacity: 1;
            transform: none;
        }
        .copilot-mark-end .copilot-mark-dot { opacity: 0; }
        .copilot-mark-end .copilot-mark-logo {
            animation: none !important;
            opacity: 1;
            transform: translate(-50%, -50%) scale(1);
        }
    }
</style>
