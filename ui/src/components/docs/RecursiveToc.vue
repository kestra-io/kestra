<template>
    <el-collapse accordion v-model="openedDocs" :key="openedDocs">
        <template
            :key="child.title"
            v-for="child in filteredChildren"
        >
            <el-collapse-item
                class="mt-1"
                :name="child.path"
                v-if="child.children"
            >
                <template #title>
                    <span v-if="disabledPages.includes(child.path) || !makeIndexNavigable">
                        {{ child.sidebarTitle?.capitalize() }}
                    </span>
                    <slot v-else v-bind="child">
                        <RouterLink :to="{path: '/' + child.path}">
                            {{ child.sidebarTitle?.capitalize() }}
                        </RouterLink>
                    </slot>
                </template>
                <RecursiveToc :parent="{children: child.children}" :makeIndexNavigable="makeIndexNavigable">
                    <template #default="subChild">
                        <slot v-bind="subChild" />
                    </template>
                </RecursiveToc>
            </el-collapse-item>
            <div v-else>
                <slot v-bind="child">
                    <RouterLink :to="{path: '/' + child.path}">
                        {{ child.sidebarTitle?.capitalize() }}
                    </RouterLink>
                </slot>
            </div>
        </template>
    </el-collapse>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue";

    defineOptions({
        name: "RecursiveToc"
    })

    defineSlots<{
        default: (child: TocChild) => any
    }>()

    const disabledPages = [
        "docs/api-reference",
        "docs/terraform/data-sources",
        "docs/terraform/guides",
        "docs/terraform/resources"
    ]

    interface TocChild {
        path: string;
        title: string;
        sidebarTitle: string;
        children?: TocChild[];
    }

    const props = withDefaults(defineProps<{
        parent: {
            children: TocChild[]
        }
        makeIndexNavigable?: boolean
    }>(), {
        makeIndexNavigable: true
    })

    const filteredChildren = computed(() => {
        return props.parent.children.filter(child => child.sidebarTitle);
    })

    const openedDocs = ref<string[]>([]);
</script>

<style scoped lang="scss">
    .el-collapse {
        --el-collapse-header-font-size: 14px;

        > * {
            font-size: var(--el-collapse-header-font-size);
            line-height: 30px;
        }

        > .el-collapse-item {
            > :deep(button) {
                padding: 0;
            }

            a {
                color: var(--ks-content-primary);

                &.RouterLink-exact-active {
                    font-weight: 700;
                }
            }
        }

        :deep(.el-collapse-item__content) {
            padding-top: 0;
            padding-bottom: 0;
        }
    }
</style>