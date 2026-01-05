<template>
    <div class="tabs-wrapper">
        <div class="tabs">
            <el-tooltip
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
            </el-tooltip>
        </div>
        <slot />
    </div>
</template>

<script setup lang="ts">
    import {Tab} from "../utils/multiPanelTypes";

    defineProps<{
        tabs: Tab[],
        openTabs: string[];
    }>();

    const emit = defineEmits<{
        (e: "update:tabs", tabValue: string): void;
    }>();

    function setTabValue(tabValue: string) {
        emit("update:tabs", tabValue);
    }
</script>

<style scoped lang="scss">
    @use "@kestra-io/ui-libs/src/scss/color-palette.scss" as colorPalette;
    
    .tabs-wrapper {
        display: flex;
        align-items: center;
        justify-content: space-between;
        border-bottom: 1px solid var(--ks-border-primary);
        background: var(--ks-background-card);
        background-size: 250% 100%;
        background-position: 100% 0;
        transition: background-position .2s;
        overflow-x: auto;
        scrollbar-width: none; 

        .dark & {
            background-image: linear-gradient(
                to right,
                colorPalette.$base-blue-500 0%,
                colorPalette.$base-blue-700 35%,
                rgba(colorPalette.$base-blue-700, .1) 55%,
                rgba(colorPalette.$base-blue-700, 0) 100%
            );
        }
    }
    
    .tabs {
        padding: .5rem 1rem;
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: .5rem;

        > button { 
            background: transparent;
            border: 1px solid transparent;
            border-radius: 6px;
            padding: 0.35rem 0.75rem;
            font-size: 0.85rem;
            white-space: nowrap;
            color: var(--ks-color-text-primary);
            display: inline-flex;
            align-items: center;
            justify-content: center;
            transition: all 0.2s ease-in-out;
            gap: 0.4rem;
            opacity: .7;

            &:hover {
                background-color: var(--ks-background-body);
                opacity: 1;
            }

            &.active {
                background-color: var(--ks-background-body);
                border-color: var(--ks-border-primary);
                color: var(--ks-color-text-primary);
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
            gap: 0.25rem;
            padding: 0.4rem 0.5rem;
        }
        
        .tabs > button {
            padding: 0.5rem;
            gap: 0;
            aspect-ratio: 1 / 1;
        }
    }
</style>
