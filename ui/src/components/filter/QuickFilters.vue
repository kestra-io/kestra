<template>
    <div class="quick-filters">
        <div v-if="showInterval" class="quick-filters__group">
            <span v-if="intervalLabel" class="quick-filters__label">{{ intervalLabel }}:</span>
            <KsSegmented
                data-test="quick-filters-interval"
                :modelValue="timeRange"
                :options="intervals"
                size="default"
                @change="emit('update:timeRange', String($event))"
            />
        </div>

        <div v-if="showLevel" class="quick-filters__group">
            <span v-if="levelLabel" class="quick-filters__label">{{ levelLabel }}:</span>
            <div
                class="quick-filters__levels"
                data-test="quick-filters-level"
                role="group"
                :aria-label="levelLabel"
            >
                <button
                    v-for="lvl in levels"
                    :key="lvl.value"
                    type="button"
                    class="quick-filters__level"
                    :class="{'quick-filters__level--active': lvl.value === level}"
                    :style="levelStyle(lvl.value)"
                    :data-test="`quick-filters-level-${lvl.value}`"
                    :aria-pressed="lvl.value === level"
                    @click="emit('update:level', lvl.value)"
                >
                    <span class="quick-filters__dot" aria-hidden="true" />
                    {{ lvl.label }}
                </button>
            </div>
        </div>

        <div v-if="showState" class="quick-filters__group">
            <span v-if="stateLabel" class="quick-filters__label">{{ stateLabel }}:</span>
            <div
                class="quick-filters__levels"
                data-test="quick-filters-state"
                role="group"
                :aria-label="stateLabel"
            >
                <button
                    v-for="item in states"
                    :key="item.value"
                    type="button"
                    class="quick-filters__level"
                    :class="{'quick-filters__level--active': state.includes(item.value)}"
                    :style="stateStyle(item.value)"
                    :data-test="`quick-filters-state-${item.value}`"
                    :aria-pressed="state.includes(item.value)"
                    @click="emit('update:state', item.value)"
                >
                    <component
                        :is="stateIcon(item.value)"
                        v-if="stateIcon(item.value)"
                        class="quick-filters__icon"
                        :size="15"
                        aria-hidden="true"
                    />
                    {{ item.label }}
                </button>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {EXECUTION_STATUSES} from "@kestra-io/design-system"

    withDefaults(defineProps<{
        levels?: Array<{label: string; value: string}>;
        intervals?: Array<{label: string; value: string}>;
        states?: Array<{label: string; value: string}>;
        level?: string;
        timeRange?: string;
        state?: string[];
        showInterval?: boolean;
        showLevel?: boolean;
        showState?: boolean;
        intervalLabel?: string;
        levelLabel?: string;
        stateLabel?: string;
    }>(), {
        levels: () => [],
        intervals: () => [],
        states: () => [],
        level: undefined,
        timeRange: undefined,
        state: () => [],
        showInterval: true,
        showLevel: true,
        showState: false,
        intervalLabel: undefined,
        levelLabel: undefined,
        stateLabel: undefined,
    })

    const emit = defineEmits<{
        "update:level": [value: string];
        "update:timeRange": [value: string];
        "update:state": [value: string];
    }>()

    const levelStyle = (value: string) => {
        const key = value.toLowerCase()
        return {
            "--level-color": `var(--ks-log-${key})`,
            "--level-bg": value === "TRACE" ? "var(--ks-bg-hover)" : `var(--ks-log-background-${key})`,
            "--level-border": `var(--ks-log-border-${key})`,
        }
    }

    const stateStyle = (value: string) => {
        const key = value.toLowerCase()
        return {
            "--level-color": `var(--ks-status-${key})`,
            "--level-bg": `var(--ks-status-background-${key})`,
            "--level-border": `var(--ks-status-border-${key})`,
        }
    }

    const stateIcon = (value: string) => EXECUTION_STATUSES[value]?.icon
</script>

<style lang="scss" scoped>
    .quick-filters {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: var(--ks-spacing-4);
        margin-top: var(--ks-spacing-2);
        margin-bottom: var(--ks-spacing-3);

        @media (max-width: 48rem) {
            flex-direction: column;
            align-items: stretch;
            gap: var(--ks-spacing-2);

            .quick-filters__group {
                width: 100%;
                overflow-x: auto;
            }
        }

        &__group {
            display: inline-flex;
            align-items: center;
            gap: var(--ks-spacing-2);
            min-width: 0;
            max-width: 100%;
        }

        &__label {
            display: inline-flex;
            align-items: center;
            height: var(--ks-spacing-6);
            font-size: var(--ks-font-size-sm);
            font-weight: 400;
            line-height: 1;
            color: var(--ks-text-secondary);
            white-space: nowrap;
        }

        &__levels {
            display: inline-flex;
            align-items: center;
            flex-wrap: wrap;
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
            color: var(--ks-text-primary);
            font-size: var(--ks-font-size-sm);
            font-weight: 500;
            white-space: nowrap;
            cursor: pointer;
            transition:
                background-color var(--ks-duration-base) ease,
                border-color var(--ks-duration-base) ease,
                color var(--ks-duration-base) ease;

            &:hover {
                background: var(--level-bg);
                color: var(--ks-text-primary);
            }

            &:focus-visible {
                outline: 2px solid var(--level-color);
                outline-offset: 2px;
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

        &__icon {
            display: inline-flex;
            align-items: center;
            color: var(--level-color);
            flex-shrink: 0;
        }
    }
</style>
