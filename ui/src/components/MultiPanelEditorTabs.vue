<template>
    <div class="tabs-wrapper">
        <div class="tabs">
            <KsTooltip
                v-for="element of tabs"
                :key="element.uid"
                :content="element.button.label"
                placement="bottom"
                :showAfter="500"
            >
                <button
                    :class="{active: openTabs.includes(element.uid)}"
                    @click="setTabValue(element.uid)"
                >
                    <component class="tabs-icon" :is="element.button.icon" />
                    <span class="tab-label">{{ element.button.label }}</span>
                </button>
            </KsTooltip>
        </div>
        <slot />
    </div>
</template>

<script setup lang="ts">
    import {Tab} from "../utils/multiPanelTypes"

    defineProps<{
        tabs: Tab[],
        openTabs: string[];
    }>()

    const emit = defineEmits<{
        (e: "update:tabs", tabValue: string): void;
    }>()

    function setTabValue(tabValue: string) {
        emit("update:tabs", tabValue)
    }
</script>

<style scoped lang="scss">
    .tabs-wrapper {
        display: flex;
        align-items: center;
        justify-content: space-between;
        border-bottom: 1px solid var(--ks-border-default);
        background: var(--ks-bg-surface);
        overflow-x: auto;
        scrollbar-width: none;
    }

    .tabs {
        padding: var(--ks-spacing-2) var(--ks-spacing-4);
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: var(--ks-spacing-1);

        > button {
            background: transparent;
            border: 1px solid transparent;
            border-radius: var(--ks-radius-base);
            padding: var(--ks-spacing-1) var(--ks-spacing-2);
            font-size: var(--ks-font-size-sm);
            white-space: nowrap;
            color: var(--ks-text-secondary);
            display: inline-flex;
            align-items: center;
            justify-content: center;
            transition: all 0.2s ease-in-out;
            gap: var(--ks-spacing-2);

            &:hover {
                background-color: var(--ks-bg-body);
            }

            &.active {
                background-color: var(--ks-btn-secondary-bg-active);
                color: var(--ks-text-link);
                opacity: 1;
            }
        }
    }

    .tabs-icon {
        font-size: 1.1em;
        vertical-align: middle;
        flex-shrink: 0;
    }

    @media (max-width: 1200px) {
        .tab-label {
            display: none;
        }

        .tabs {
            gap: var(--ks-spacing-1);
            padding: var(--ks-spacing-2);
        }

        .tabs > button {
            padding: var(--ks-spacing-2);
            gap: 0;
            aspect-ratio: 1 / 1;
        }
    }
</style>
