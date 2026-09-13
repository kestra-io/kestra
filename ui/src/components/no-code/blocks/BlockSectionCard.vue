<template>
    <section class="block-section" :class="`block-section--${tone}`" :data-test="`block-section-${name}`">
        <header class="block-section-head" :data-test="`block-editor-section-head-${name}`">
            <div class="block-section-title">
                <component :is="icon" class="block-section-ico" />
                <span class="block-section-title-text">{{ title }}</span>
                <span v-if="!hideCount" class="block-section-count" :class="{'block-section-count--active': count > 0}">{{ count }}</span>
            </div>

            <button
                class="block-section-add"
                type="button"
                :data-test="addTest"
                :aria-label="addLabel"
                :title="addLabel"
                @click="emit('add', $event)"
            >
                <component :is="actionIcon ?? Plus" class="block-section-add-ico" />
                <span class="block-section-add-label">{{ addLabel }}</span>
            </button>
        </header>

        <div class="block-section-body">
            <slot />
        </div>
    </section>
</template>

<script setup lang="ts">
    import type {Component} from "vue"
    import Plus from "vue-material-design-icons/Plus.vue"

    withDefaults(defineProps<{
        name: string
        title: string
        icon: Component
        count: number
        addLabel: string
        tone?: "default" | "error" | "warning"
        addTest?: string
        actionIcon?: Component
        hideCount?: boolean
    }>(), {
        tone: "default",
        hideCount: false,
    })

    const emit = defineEmits<{
        (e: "add", evt: MouseEvent): void
    }>()
</script>

<style scoped lang="scss">
    .block-section {
        container-type: inline-size;
        background: var(--ks-bg-surface);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-lg);
        overflow: hidden;
    }

    .block-section-head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-3) var(--ks-spacing-4);
        border-bottom: 1px solid var(--ks-border-subtle);
    }

    .block-section-title {
        display: flex;
        align-items: center;
        min-width: 0;
        gap: var(--ks-spacing-2);
        font-size: var(--ks-font-size-base);
        font-weight: 600;
        color: var(--ks-text-primary);
    }

    .block-section-title-text {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .block-section-ico {
        display: flex;
        font-size: var(--ks-font-size-base);
        color: var(--ks-text-secondary);

        .block-section--error & {
            color: var(--ks-text-error);
        }

        .block-section--warning & {
            color: var(--ks-text-warning);
        }
    }

    .block-section-count {
        flex-shrink: 0;
        font-size: var(--ks-font-size-xs);
        font-weight: 600;
        font-family: var(--ks-font-family-mono);
        padding: 0 var(--ks-spacing-2);
        border-radius: var(--ks-radius-lg);
        background: var(--ks-bg-tag-inactive);
        color: var(--ks-text-muted);
    }

    .block-section-count--active {
        background: var(--ks-bg-tag-hover);
        color: var(--ks-text-link);

        .block-section--error & {
            background: var(--ks-bg-error);
            color: var(--ks-text-error);
        }

        .block-section--warning & {
            background: var(--ks-bg-warning);
            color: var(--ks-text-warning);
        }
    }

    .block-section-add {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        flex-shrink: 0;
        font-size: var(--ks-font-size-sm);
        font-weight: 500;
        color: var(--ks-text-secondary);
        background: transparent;
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        padding: var(--ks-spacing-1) var(--ks-spacing-3);
        cursor: pointer;
        transition: color 0.12s, border-color 0.12s, background-color 0.12s;

        &:hover {
            color: var(--ks-text-primary);
            border-color: var(--ks-border-strong);
            background: var(--ks-btn-secondary-bg-hover);
        }

        &:focus-visible {
            outline: 2px solid var(--ks-border-focus);
            outline-offset: 1px;
        }
    }

    .block-section-add-ico {
        display: flex;
        font-size: var(--ks-font-size-sm);
    }

    @container (max-width: 280px) {
        .block-section-add-label {
            display: none;
        }

        .block-section-add {
            padding: var(--ks-spacing-1) var(--ks-spacing-2);
        }
    }

    .block-section-body {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-3);
    }
</style>
