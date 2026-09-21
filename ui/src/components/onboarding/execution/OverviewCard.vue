<template>
    <!-- Anchor attributes are only bound for the external-link variant: passing them as
         `undefined` would fall through onto RouterLink's anchor and wipe the href it renders,
         losing the native link cursor (https://github.com/kestra-io/kestra/issues/18148). -->
    <component
        :is="to ? RouterLink : link ? 'a' : 'div'"
        :to="to"
        v-bind="link ? {href: link, target: '_blank', rel: 'noopener noreferrer'} : {}"
        class="card"
    >
        <KsIcon class="icon">
            <component :is="icon" />
        </KsIcon>
        <div class="text">
            <h5 class="title">
                {{ title }}
            </h5>
            <p class="desc">
                {{ description }}
            </p>
        </div>
        <KsIcon v-if="link || to" class="open">
            <OpenInNew v-if="link" />
            <ChevronRight v-else />
        </KsIcon>
    </component>
</template>

<script setup lang="ts">
    import type {Component} from "vue"
    import {RouterLink, type RouteLocationRaw} from "vue-router"
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"

    defineProps<{
        title: string;
        description: string;
        link?: string;
        to?: RouteLocationRaw;
        icon?: Component;
    }>()
</script>

<style scoped lang="scss">
.card {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-4);
    width: 100%;
    padding: var(--ks-spacing-4) var(--ks-spacing-5);
    background-color: var(--ks-bg-surface);
    text-align: left;
    text-decoration: none;

    &:hover {
        background-color: var(--ks-bg-hover);

        .icon {
            color: var(--ks-icon-hover);
        }
    }

    .icon {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: var(--ks-icon-size-xl);
        height: var(--ks-icon-size-lg);
        color: var(--ks-icon-muted);

        :deep(svg) {
            width: var(--ks-icon-size-lg);
            height: var(--ks-icon-size-lg);
        }
    }

    .text {
        flex: 1;

        .title {
            margin: 0;
            font-size: var(--ks-font-size-md);
            font-weight: var(--ks-font-weight-semibold);
            color: var(--ks-text-primary);
        }

        .desc {
            margin: 0;
            font-size: var(--ks-font-size-sm);
            font-weight: var(--ks-font-weight-regular);
            line-height: var(--ks-line-height-base);
            color: var(--ks-text-secondary);
        }
    }

    .open {
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-base);
    }
}
</style>
