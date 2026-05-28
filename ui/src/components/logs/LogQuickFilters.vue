<template>
    <div class="log-quick-filters">
        <div v-if="showInterval" class="log-quick-filters__group">
            <span v-if="intervalLabel" class="log-quick-filters__label">{{ intervalLabel }}</span>
            <KsSegmented
                data-test="log-quick-filters-interval"
                :modelValue="timeRange"
                :options="intervals"
                size="default"
                @change="emit('update:timeRange', String($event))"
            />
        </div>

        <div class="log-quick-filters__group">
            <span v-if="levelLabel" class="log-quick-filters__label">{{ levelLabel }}</span>
            <div
                class="log-quick-filters__levels"
                data-test="log-quick-filters-level"
                role="group"
            >
                <button
                    v-for="lvl in levels"
                    :key="lvl.value"
                    type="button"
                    class="log-quick-filters__level"
                    :class="{'log-quick-filters__level--active': lvl.value === level}"
                    :style="levelStyle(lvl.value)"
                    :data-test="`log-quick-filters-level-${lvl.value}`"
                    :aria-pressed="lvl.value === level"
                    @click="emit('update:level', lvl.value)"
                >
                    <span class="log-quick-filters__dot" aria-hidden="true" />
                    {{ lvl.label }}
                </button>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    withDefaults(defineProps<{
        levels: Array<{label: string; value: string}>;
        intervals?: Array<{label: string; value: string}>;
        level?: string;
        timeRange?: string;
        showInterval?: boolean;
        intervalLabel?: string;
        levelLabel?: string;
    }>(), {
        intervals: () => [],
        level: undefined,
        timeRange: undefined,
        showInterval: true,
        intervalLabel: undefined,
        levelLabel: undefined,
    })

    const emit = defineEmits<{
        "update:level": [value: string];
        "update:timeRange": [value: string];
    }>()

    // Each level pill is tinted with its semantic log color. Exposed as CSS
    // custom properties so the scoped styles can theme idle/hover/active states
    // from a single source per level (resolves through the --ks-log-* tokens).
    const levelStyle = (value: string) => {
        const key = value.toLowerCase()
        return {
            "--level-color": `var(--ks-log-${key})`,
            "--level-bg": `var(--ks-log-background-${key})`,
            "--level-border": `var(--ks-log-border-${key})`,
        }
    }
</script>

<style lang="scss" scoped>
    .log-quick-filters {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: var(--ks-spacing-4);
        margin-top: var(--ks-spacing-2);

        &__group {
            display: inline-flex;
            align-items: center;
            gap: var(--ks-spacing-2);
        }

        &__label {
            font-size: var(--ks-font-size-sm);
            font-weight: 500;
            color: var(--ks-text-secondary);
            white-space: nowrap;
        }

        &__levels {
            display: inline-flex;
            align-items: center;
            gap: var(--ks-spacing-1);
        }

        &__level {
            display: inline-flex;
            align-items: center;
            gap: var(--ks-spacing-1);
            height: var(--ks-spacing-6);
            padding: 0 var(--ks-spacing-3);
            border: 1px solid transparent;
            border-radius: var(--ks-radius-lg);
            background: transparent;
            color: var(--ks-text-secondary);
            font-size: var(--ks-font-size-sm);
            line-height: 1;
            white-space: nowrap;
            cursor: pointer;
            transition:
                background-color 0.15s ease,
                border-color 0.15s ease,
                color 0.15s ease;

            &:hover {
                background: var(--level-bg);
                color: var(--ks-text-primary);
            }

            &--active {
                background: var(--level-bg);
                border-color: var(--level-border);
                color: var(--ks-text-primary);
                font-weight: 500;
            }
        }

        &__dot {
            width: var(--ks-spacing-2);
            height: var(--ks-spacing-2);
            border-radius: 50%;
            background: var(--level-color);
            flex-shrink: 0;
        }
    }
</style>
