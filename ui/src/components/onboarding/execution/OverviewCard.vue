<template>
    <el-card class="card">
        <div v-if="showIcon" class="header">
            <el-link underline="never" :href="link" target="_blank">
                <el-icon class="el-icon--right">
                    <OpenInNew />
                </el-icon>
            </el-link>
        </div>
        <div class="icon-row">
            <el-icon :size="24">
                <component :is="icon" />
            </el-icon>
            <div>
                <h5 class="title">
                    {{ title }}
                </h5>
                <div class="desc">
                    <Markdown :source="$t(`execution_guide.${category}.text`)" />
                </div>
            </div>
        </div>
    </el-card>
</template>

<script setup lang="ts">
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue";
    import Markdown from "../../layout/Markdown.vue";

    defineProps<{
        title: string;
        category: string;
        content?: string;
        link?: string;
        icon?: any;
        showIcon?: boolean;
    }>();
</script>

<style scoped lang="scss">
.el-card {
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

        :deep(.el-icon) {
            color: var(--ks-content-secondary);
            font-size: 1rem;
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
        font-size: 0.875rem;
        line-height: 1.375rem;
    }

    .desc {
        margin: 0;
        font-size: 0.75rem;
        line-height: 1.25rem;
        color: var(--ks-content-secondary);
    }
}

.icon-row {
    display: inline-flex;
    gap: 1rem;
}
</style>
