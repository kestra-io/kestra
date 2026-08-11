<template>
    <nav class="field-nav-breadcrumb" :aria-label="t('no_code.nav.breadcrumb_aria')">
        <button
            type="button"
            class="field-nav-back"
            :aria-label="t('no_code.nav.back')"
            @click="emit('back')"
        >
            <ChevronLeft :size="16" />
            <span>{{ t("no_code.nav.back") }}</span>
        </button>

        <span class="field-nav-sep">/</span>

        <button type="button" class="field-nav-crumb" @click="emit('navigate', -1)">
            {{ rootLabel }}
        </button>

        <template v-for="(frame, index) in frames" :key="frame.path">
            <ChevronRight class="field-nav-chevron" :size="14" />
            <button
                type="button"
                class="field-nav-crumb"
                :class="{'field-nav-crumb--current': index === frames.length - 1}"
                :disabled="index === frames.length - 1"
                @click="emit('navigate', index)"
            >
                {{ frame.label }}
            </button>
        </template>
    </nav>
</template>

<script setup lang="ts">
    import {useI18n} from "vue-i18n"
    import ChevronLeft from "vue-material-design-icons/ChevronLeft.vue"
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"

    import type {Crumb} from "../utils/useFieldNavigation"

    const {t} = useI18n()

    defineProps<{
        frames: Crumb[];
        rootLabel: string;
    }>()

    const emit = defineEmits<{
        (e: "navigate", index: number): void;
        (e: "back"): void;
    }>()
</script>

<style scoped lang="scss">
    .field-nav-breadcrumb {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        flex-wrap: wrap;
        margin-bottom: var(--ks-spacing-4);
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-sm);
    }

    .field-nav-back {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        background: var(--ks-bg-surface);
        color: var(--ks-text-secondary);
        font-family: var(--ks-font-family-sans);
        font-size: var(--ks-font-size-sm);
        padding: 2px var(--ks-spacing-2);
        cursor: pointer;
        transition: color 0.15s ease, border-color 0.15s ease, scale 0.1s ease;

        &:hover {
            color: var(--ks-text-primary);
            border-color: var(--ks-border-strong);
        }

        &:active {
            scale: 0.96;
        }

        &:focus-visible {
            outline: 2px solid var(--ks-border-focus);
            outline-offset: 1px;
        }
    }

    .field-nav-sep,
    .field-nav-chevron {
        color: var(--ks-icon-muted);
        display: inline-flex;
    }

    .field-nav-crumb {
        border: none;
        background: none;
        padding: 0;
        color: var(--ks-text-secondary);
        font-family: inherit;
        font-size: inherit;
        cursor: pointer;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        max-width: 12rem;
        transition: color 0.15s ease, scale 0.1s ease;

        &:hover:not(:disabled) {
            color: var(--ks-text-link);
        }

        &:active:not(:disabled) {
            scale: 0.96;
        }

        &:focus-visible {
            outline: 2px solid var(--ks-border-focus);
            outline-offset: 2px;
            border-radius: var(--ks-radius-xs);
        }
    }

    .field-nav-crumb--current {
        color: var(--ks-text-primary);
        font-weight: 600;
        cursor: default;
    }
</style>
