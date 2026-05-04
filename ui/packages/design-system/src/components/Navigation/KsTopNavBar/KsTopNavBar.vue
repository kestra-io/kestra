<template>
    <nav class="d-flex align-items-center w-100 gap-3 ks-topnavbar">
        <slot name="sidebar-toggle" />
        <div class="d-flex flex-column flex-grow-1 flex-shrink-1 overflow-hidden ks-top-title">
            <div class="d-flex align-items-end gap-2">
                <div class="d-flex flex-column gap-2">
                    <KsBreadcrumb v-if="breadcrumb">
                        <KsBreadcrumbItem
                            v-for="(item, x) in breadcrumb"
                            :key="x"
                            :class="{'pe-none': item.disabled}"
                        >
                            <a v-if="item.disabled || !item.link">{{ item.label }}</a>
                            <component v-else :is="RouterLink" :to="item.link">
                                {{ item.label }}
                            </component>
                        </KsBreadcrumbItem>
                    </KsBreadcrumb>
                    <h1 class="h5 fw-semibold m-0 d-inline-flex">
                        <slot name="title">
                            {{ title }}
                            <KsTooltip v-if="description" :content="description">
                                <Information class="ms-2 icon" />
                            </KsTooltip>
                            <span v-if="beta" class="beta-badge">Beta</span>
                            <template v-if="$slots.badge">
                                <span class="ks-badge">
                                    <slot name="badge" />
                                </span>
                            </template>
                        </slot>
                        <KsButton
                            class="icon"
                            :class="{'active': isBookmarked}"
                            :icon="isBookmarked ? StarIcon : StarOutlineIcon"
                            circle
                            @click="$emit('star-click')"
                        />
                    </h1>
                    <div class="description">
                        <slot name="description">
                            {{ longDescription }}
                        </slot>
                    </div>
                </div>
            </div>
        </div>
        <div class="d-lg-flex side gap-2 flex-shrink-0 align-items-center ks-side-container">
            <div class="d-none d-lg-flex align-items-center" v-if="$slots.search">
                <slot name="search" />
            </div>
            <div class="d-flex side gap-2 flex-shrink-0 align-items-center" v-if="$slots['pre-action']">
                <slot name="pre-action" />
            </div>
            <div class="d-flex side gap-2 flex-shrink-0 align-items-center" v-if="$slots['more-actions']">
                <KsDropdown>
                    <KsButton class="more-actions" type="default" :icon="DotsVertical" />
                    <template #dropdown>
                        <KsDropdownMenu>
                            <slot v-if="$slots['more-actions']" name="more-actions" />
                        </KsDropdownMenu>
                    </template>
                </KsDropdown>
            </div>
            <slot v-if="$slots.actions" name="actions" />
        </div>
    </nav>
</template>

<script setup lang="ts">
    import {resolveComponent} from "vue"
    import StarOutlineIcon from "vue-material-design-icons/StarOutline.vue"
    import StarIcon from "vue-material-design-icons/Star.vue"
    import Information from "vue-material-design-icons/Information.vue"
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue"
    import KsBreadcrumb from "../KsBreadcrumb/KsBreadcrumb.vue"
    import KsBreadcrumbItem from "../KsBreadcrumb/KsBreadcrumbItem.vue"
    import KsButton from "../../Basic/KsButton/KsButton.vue"
    import KsTooltip from "../../Feedback/KsTooltip.vue"

    defineProps<{
        title: string
        description?: string
        longDescription?: string
        breadcrumb?: {
            label: string
            link?: any
            disabled?: boolean
        }[]
        beta?: boolean
        isBookmarked?: boolean
    }>()

    defineEmits<{
        "star-click": []
    }>()

    defineSlots<{
        "sidebar-toggle"?(): unknown
        title?(): unknown
        badge?(): unknown
        description?(): unknown
        search?(): unknown
        "pre-action"?(): unknown
        "more-actions"?(): unknown
        "actions"?(): unknown
    }>()

    const RouterLink = resolveComponent("RouterLink")
</script>

<style scoped lang="scss">
    nav {
        top: 0;
        position: sticky;
        z-index: 1000;
        padding: 1rem 2rem;
        border-bottom: 1px solid var(--ks-border-primary);
        background: var(--ks-background-card);

        .ks-top-title, h1, .kel-breadcrumb {
            white-space: nowrap;
            max-width: 100%;
            text-overflow: ellipsis;
            overflow: hidden;
        }

        .ks-top-title {
            position: relative;

            &::after {
                content: "";
                position: absolute;
                top: 0;
                right: 0;
                width: 40px;
                height: 100%;
                background: linear-gradient(to left, var(--ks-background-card), transparent);
                pointer-events: none;
            }
        }

        h1 {
            font-size: var(--ks-font-size-lg);
            line-height: 1.6;
            display: flex !important;
            align-items: center;
        }

        .ks-badge {
            margin-left: 0.5rem;
        }

        .description {
            font-size: var(--ks-font-size-sm);
            margin-top: -0.5rem;
            color: var(--ks-content-secondary);
        }

        .more-actions {
            border: none;
        }

        .icon {
            border: none;
            color: var(--ks-content-tertiary);

            &:deep(svg) {
                fill: currentColor;
                stroke: currentColor;
            }

            &.active {
                color: var(--ks-content-link-hover);
            }
        }

        :deep(.kel-breadcrumb__item) {
            display: inline-block;
        }

        :deep(.kel-breadcrumb__inner) {
            white-space: nowrap;
            max-width: 100%;
            text-overflow: ellipsis;
            overflow: hidden;
        }

        .side {
            :slotted(ul), :deep(ul) {
                display: flex;
                list-style: none;
                padding: 0;
                margin: 0;
                gap: .5rem;
                align-items: center;
            }
        }

        @media (max-width: 992px) {
            padding: 0.75rem 1.5rem;
        }

        @media (max-width: 768px) {
            padding: 0.75rem;

            .ks-side-container {
                display: grid;
                grid-template-columns: repeat(3, minmax(0, auto));
                grid-template-rows: repeat(2, auto);
                gap: 10px;
                overflow: hidden;
            }
        }

        @media (max-width: 664px) {
            padding: 0.75rem 0.5rem;

            .ks-side-container {
                display: grid;
                grid-template-columns: repeat(2, minmax(0, auto));
                grid-template-rows: repeat(2, auto);
                gap: 10px;
                overflow: hidden;
            }
        }
    }
</style>
