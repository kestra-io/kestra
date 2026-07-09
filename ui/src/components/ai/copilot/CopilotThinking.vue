<template>
    <!-- Shown while the model is working before its next output arrives. -->
    <div class="copilot-thinking" data-test="copilot-thinking">
        <KsText size="small" class="copilot-thinking-label">{{ t("ai.copilot.thinking") }}</KsText><span
            class="copilot-thinking-dots"
            aria-hidden="true"
        />
    </div>
</template>

<script setup lang="ts">
    import {useI18n} from "vue-i18n"

    const {t} = useI18n()
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
    }

    /*
        Animated "rising dots": 1 → 3 then back to 1, on a loop. Driven purely by a CSS
        keyframe animation on the pseudo-element's `content` (discrete steps, no timers to
        clean up). Sits at the end of the line so growing/shrinking never reflows other text.
    */
    .copilot-thinking-dots::after {
        content: ".";
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
        animation: copilot-thinking-dots 1.2s linear infinite;
    }

    @keyframes copilot-thinking-dots {
        0%   { content: "."; }
        33%  { content: ".."; }
        66%  { content: "..."; }
        100% { content: "."; }
    }
</style>
