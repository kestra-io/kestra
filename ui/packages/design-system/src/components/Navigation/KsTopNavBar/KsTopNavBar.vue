<template>
    <nav class="ks-topnavbar d-flex align-items-center w-100">
        <KsIconButton
            v-if="sidebarCollapsed"
            class="icon-btn"
            data-testid="topnav-sidebar-toggle"
            :ariaLabel="t('topnav_sidebar_toggle')"
            @click="$emit('sidebar-toggle')"
        >
            <Menu />
        </KsIconButton>
        <div class="title-section">
            <div class="d-flex align-items-center gap-2">
                <KsBreadcrumb
                    :items="breadcrumb ?? []"
                    :title="title ?? ''"
                    :mainIcon="mainIcon"
                    showLeading
                >
                    <template #title>
                        <slot name="title">{{ title }}</slot>
                    </template>
                </KsBreadcrumb>
                <KsTooltip v-if="description" :content="description">
                    <Information class="info-icon" />
                </KsTooltip>
                <KsTag v-if="beta" type="primary" size="small" class="beta-tag">Beta</KsTag>
                <KsIconButton
                    class="icon-btn star"
                    :class="{active: isBookmarked}"
                    :ariaLabel="t('topnav_bookmark')"
                    @click="$emit('star-click')"
                >
                    <component :is="isBookmarked ? StarIcon : StarOutlineIcon" />
                </KsIconButton>
                <KsSelect
                    v-if="tabs && tabs.length"
                    :modelValue="activeTab"
                    class="tab-select"
                    size="small"
                    @change="(v) => $emit('tab-change', v as string)"
                >
                    <KsOption
                        v-for="tab in tabs"
                        :key="tab.name ?? 'default'"
                        :label="tab.title"
                        :value="tab.name ?? 'default'"
                        :disabled="tab.disabled"
                    />
                </KsSelect>
            </div>
            <div v-show="showDescription" class="description">
                <slot name="description" />
            </div>
        </div>
        <div class="side d-flex gap-2 flex-shrink-0 align-items-center">
            <slot name="search" />
            <slot name="actions" />
            <KsIconButton
                v-if="showDockToggle"
                class="icon-btn dock-toggle"
                :class="{'is-open': isDockOpen}"
                :ariaLabel="t('topnav_dock_toggle')"
                @click="$emit('dock-toggle')"
            >
                <DockRight />
            </KsIconButton>
        </div>
    </nav>
</template>

<script setup lang="ts">
    import type {Component} from "vue"
    import {useI18n} from "vue-i18n"
    import Menu from "vue-material-design-icons/Menu.vue"
    import StarOutlineIcon from "vue-material-design-icons/StarOutline.vue"
    import StarIcon from "vue-material-design-icons/Star.vue"
    import Information from "vue-material-design-icons/InformationOutline.vue"
    import DockRight from "vue-material-design-icons/DockRight.vue"
    import KsBreadcrumb from "../KsBreadcrumb/KsBreadcrumb.vue"
    import type {KsBreadcrumbItem} from "../KsBreadcrumb/types"
    import KsIconButton from "../../Basic/KsIconButton/KsIconButton.vue"
    import KsTooltip from "../../Feedback/KsTooltip.vue"
    import KsTag from "../../Data/KsTag/KsTag.vue"
    import KsSelect from "../../Form/KsSelect/KsSelect.vue"
    import KsOption from "../../Form/KsSelect/KsOption.vue"

    export interface KsTopNavBarTab {
        name?: string
        title: string
        disabled?: boolean
    }

    defineProps<{
        title?: string
        description?: string
        breadcrumb?: KsBreadcrumbItem[]
        mainIcon?: Component
        beta?: boolean
        isBookmarked?: boolean
        sidebarCollapsed?: boolean
        tabs?: KsTopNavBarTab[]
        activeTab?: string
        showDescription?: boolean
        showDockToggle?: boolean
        isDockOpen?: boolean
    }>()

    defineEmits<{
        "star-click": []
        "sidebar-toggle": []
        "tab-change": [value: string]
        "dock-toggle": []
    }>()

    defineSlots<{
        title?(): unknown
        description?(): unknown
        search?(): unknown
        actions?(): unknown
    }>()

    const {t} = useI18n({useScope: "global"})
</script>

<style scoped lang="scss">
    .ks-topnavbar {
        height: 60px;
        flex-shrink: 0;
        padding: 0 var(--ks-spacing-6);
        gap: var(--ks-spacing-4);
        border-bottom: var(--ks-border-block-primary);
        background: var(--ks-bg-surface);

        @media (max-width: 992px) {
            padding: 0 var(--ks-spacing-5);
        }

        @media (max-width: 768px) {
            padding: 0 var(--ks-spacing-3);
        }

        @media (max-width: 664px) {
            padding: 0 var(--ks-spacing-2);
        }
    }

    .title-section {
        flex: 1 1 auto;
        min-width: 0;
        overflow: hidden;
    }

    .description {
        font-size: var(--ks-font-size-sm);
        margin-top: var(--ks-spacing-1);
        color: var(--ks-text-secondary);
    }

    .tab-select {
        width: auto;
        min-width: 140px;
        max-width: 220px;
        flex: 0 0 auto;
    }

    .info-icon {
        color: var(--ks-text-dim);
    }

    .beta-tag {
        flex-shrink: 0;
    }

    .icon-btn {
        border: none;
        color: var(--ks-text-dim);

        &:deep(svg) {
            fill: currentColor;
            stroke: currentColor;
        }

        &.active {
            color: var(--ks-text-link);
        }
    }

    .dock-toggle {
        &.is-open {
            color: var(--ks-icon-default);
        }

        @media (max-width: 767px) {
            display: none;
        }
    }

    .side {
        :deep(ul) {
            display: flex;
            list-style: none;
            padding: 0;
            margin: 0;
            gap: var(--ks-spacing-2);
            align-items: center;
        }
    }
</style>
