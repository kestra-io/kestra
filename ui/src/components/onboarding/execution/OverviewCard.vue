<template>
    <KsCard class="card">
        <div v-if="showIcon" class="header">
            <KsLink :href="link" target="_blank">
                <KsIcon class="el-icon--right">
                    <OpenInNew />
                </KsIcon>
            </KsLink>
        </div>
        <div class="icon-row">
            <KsIcon size="lg">
                <component :is="icon" />
            </KsIcon>
            <div>
                <h5 class="title">
                    {{ title }}
                </h5>
                <div class="desc">
                    <KsMarkdown :content="$t(`execution_guide.${category}.text`)" />
                </div>
            </div>
        </div>
    </KsCard>
</template>

<script setup lang="ts">
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"
    import {KsMarkdown} from "@kestra-io/design-system"

    defineProps<{
        title: string;
        category: string;
        content?: string;
        link?: string;
        icon?: any;
        showIcon?: boolean;
    }>()
</script>

<style scoped lang="scss">
.kel-card {
    background-color: var(--ks-background-card);
    border-color: var(--ks-border-primary);
    box-shadow: 0px 2px 4px 0px var(--ks-card-shadow);
    position: relative;
    min-width: 100%;
    min-height: 8.625rem;
    border-radius: 0.5rem;
    cursor: pointer;
    text-align: left;
}

.card {
    .header {
        position: absolute;
        top: 0.3125rem;
        right: 0.3125rem;

        :deep(.kel-icon) {
            color: var(--ks-content-secondary);
            font-size: var(--ks-font-size-base);
            position: absolute;
            top: -0.875rem;
            right: 0;

            &:hover {
                color: var(--ks-content-tertiary);
            }
        }
    }

    .title {
        font-weight: 700;
        font-size: var(--ks-font-size-sm);
        line-height: 1.375rem;
    }

    .desc {
        margin: 0;
        font-size: var(--ks-font-size-xs);
        line-height: 1.25rem;
        color: var(--ks-content-secondary);
    }
}

.icon-row {
    display: inline-flex;
    gap: 1rem;
}
</style>
