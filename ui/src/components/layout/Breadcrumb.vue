<template>
    <el-breadcrumb>
        <el-breadcrumb-item
            v-for="(item, index) in visibleItems.before"
            :key="`before-${index}`"
            :to="item.disabled ? undefined : item.link"
            :class="{'pe-none': item.disabled}"
        >
            {{ item.label }}
        </el-breadcrumb-item>

        <el-breadcrumb-item v-if="visibleItems.collapsed.length > 0">
            <el-tooltip placement="bottom">
                <template #content>
                    <template v-for="(item, i) in visibleItems.collapsed" :key="i">
                        <RouterLink v-if="item.link" :to="item.link" class="collapsed-link">
                            {{ item.label }}
                        </RouterLink>
                        <span v-else class="collapsed-label">
                            {{ item.label }}
                        </span>
                    </template>
                </template>
                <span class="breadcrumb-ellipsis">...</span>
            </el-tooltip>
        </el-breadcrumb-item>

        <el-breadcrumb-item
            v-for="(item, index) in visibleItems.after"
            :key="`after-${index}`"
            :to="item.disabled ? undefined : item.link"
            :class="{'pe-none': item.disabled}"
        >
            {{ item.label }}
        </el-breadcrumb-item>

        <el-breadcrumb-item class="fw-semibold">
            <h1 class="breadcrumb-title">
                <slot />
            </h1>
        </el-breadcrumb-item>
    </el-breadcrumb>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {RouterLink} from "vue-router";

    type RouterLinkTo = InstanceType<typeof RouterLink>["$props"]["to"];

    export interface BreadcrumbItem {
        label: string;
        link?: RouterLinkTo;
        disabled?: boolean;
    }

    const props = defineProps<{
        items: BreadcrumbItem[];
    }>();

    const MAX_VISIBLE_ITEMS = 3;
    const VISIBLE_AT_END = 1;

    const visibleItems = computed(() => {
        const items = props.items;

        if (items.length <= MAX_VISIBLE_ITEMS) {
            return {
                before: items,
                collapsed: [],
                after: []
            };
        }

        return {
            before: items.slice(0, 1),
            collapsed: items.slice(1, -VISIBLE_AT_END),
            after: items.slice(-VISIBLE_AT_END)
        };
    });
</script>

<style scoped lang="scss">
    :deep(.el-breadcrumb__item) {
        display: inline-block;

        .el-breadcrumb__separator {
            color: var(--ks-content-secondary);
            opacity: 0.6;
        }
    }

    :deep(.el-breadcrumb__inner) {
        white-space: nowrap;
        max-width: 100%;
        text-overflow: ellipsis;
        overflow: hidden;
    }

    :deep(.el-breadcrumb__inner.is-link),
    .breadcrumb-ellipsis,
    .collapsed-link {
        color: var(--ks-content-secondary);
        opacity: 0.6;
        cursor: pointer;
        font-weight: normal;

        &:hover {
            color: var(--ks-content-primary);
            opacity: 1;
        }
    }

    .collapsed-link, .collapsed-label {
        display: block;
        text-decoration: none;
    }

    .breadcrumb-title {
        font-size: inherit;
        font-weight: inherit;
        display: inline;
    }
</style>
