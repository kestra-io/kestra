<template>
    <div class="tabs-wrapper">
        <div v-if="!isMobile" class="tabs">
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
        <KsDropdown
            v-else
            trigger="click"
            :hideOnClick="false"
            class="mobile-tabs-dropdown"
        >
            <KsButton>
                {{ $t("select view") }}
                <ChevronDown class="chevron" />
            </KsButton>
            <template #dropdown>
                <KsDropdownMenu>
                    <KsDropdownItem
                        v-for="element of tabs"
                        :key="element.uid"
                        :class="{active: openTabs.includes(element.uid)}"
                        @click="setTabValue(element.uid)"
                    >
                        <component class="tabs-icon" :is="element.button.icon" />
                        <span class="tab-label">{{ element.button.label }}</span>
                        <Check v-if="openTabs.includes(element.uid)" class="check-icon" />
                    </KsDropdownItem>
                </KsDropdownMenu>
            </template>
        </KsDropdown>
        <slot />
    </div>
</template>

<script setup lang="ts">
    import {Tab} from "../utils/multiPanelTypes"
    import {useMediaQuery} from "@vueuse/core"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import Check from "vue-material-design-icons/Check.vue"

    defineProps<{
        tabs: Tab[],
        openTabs: string[];
    }>()

    const emit = defineEmits<{
        (e: "update:tabs", tabValue: string): void;
    }>()

    const isMobile = useMediaQuery("(max-width: 768px)")

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

    .mobile-tabs-dropdown {
        padding: var(--ks-spacing-2) var(--ks-spacing-4);

        .chevron {
            margin-left: var(--ks-spacing-1);
            display: inline-flex;
            align-items: center;
        }
    }

    @media (max-width: 1200px) {
        .tabs .tab-label {
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
