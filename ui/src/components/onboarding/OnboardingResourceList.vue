<template>
    <div class="onboarding-resource-list">
        <component
            :is="item.href ? 'a' : 'router-link'"
            v-for="item in items"
            :key="item.titleKey"
            class="onboarding-resource-item"
            :href="item.href"
            :to="item.to"
            :target="item.href ? '_blank' : undefined"
            :rel="item.href ? 'noreferrer' : undefined"
        >
            <div class="onboarding-resource-item__icon" :class="item.iconClass">
                <component :is="item.icon" />
            </div>

            <div class="onboarding-resource-item__content">
                <h3>{{ $t(item.titleKey) }}</h3>
                <p>{{ $t(item.descriptionKey) }}</p>
            </div>

            <ChevronRight class="onboarding-resource-item__arrow" />
        </component>
    </div>
</template>

<script setup lang="ts">
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue";

    export interface OnboardingResourceItem {
        titleKey: string;
        descriptionKey: string;
        icon: any;
        iconClass: string;
        to?: any;
        href?: string;
    }

    defineProps<{
        items: OnboardingResourceItem[];
    }>();
</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/_variables.scss";

    .onboarding-resource-list {
        overflow: hidden;
        border: 1px solid var(--ks-border-primary);
        border-radius: 14px;
        background: var(--ks-background-card);
    }

    .onboarding-resource-item {
        display: flex;
        align-items: center;
        gap: 1rem;
        padding: 1rem 1.25rem;
        color: inherit;
        cursor: pointer;
        text-decoration: none;
        transition: background-color 0.15s ease;

        &:not(:last-child) {
            border-bottom: 1px solid var(--ks-border-primary);
        }

        &:hover {
            background: var(--ks-button-background-secondary);
            text-decoration: none;
        }
    }

    .onboarding-resource-item__icon {
        display: grid;
        place-items: center;
        width: 28px;
        height: 28px;
        flex-shrink: 0;

        &:deep(svg) {
            width: 22px;
            height: 22px;
        }

        &.is-tutorial {
            color: #4dabf7;
        }

        &.is-blueprints {
            color: #8b5cf6;
        }

        &.is-slack {
            color: #22c55e;
        }

        &.is-videos {
            color: #f87171;
        }

        &.is-demo {
            color: #fb923c;
        }
    }

    .onboarding-resource-item__content {
        flex: 1;
        min-width: 0;

        h3 {
            margin: 0 0 0.25rem;
            color: var(--ks-content-primary);
            font-size: $font-size-sm;
            font-weight: 600;
        }

        p {
            margin: 0;
            color: var(--ks-content-secondary);
            font-size: $font-size-sm;
            line-height: 1.4;
        }
    }

    .onboarding-resource-item__arrow {
        color: var(--ks-content-tertiary);
        flex-shrink: 0;
    }
</style>
