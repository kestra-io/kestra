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
                    <span v-if="disabledPages.includes(child.path) || !makeIndexNavigable" :class="`depth-${depth}`">
                        {{ child.title.capitalize() }}
                    </span>
                    <slot v-else v-bind="child" :class="`depth-${depth}`">
                        <RouterLink :to="{path: '/' + child.path}" :class="`depth-${depth}`">
                            {{ child.title.capitalize() }}
                        </RouterLink>
                    </slot>
                </template>
                <RecursiveToc :parent="{children: child.children}" :makeIndexNavigable="makeIndexNavigable" :depth="depth + 1">
                    <template #default="subChild">
                        <slot v-bind="subChild" />
                    </template>
                </RecursiveToc>
            </el-collapse-item>
            <div v-else>
                <slot v-bind="child" :class="`depth-${depth}`">
                    <RouterLink :to="{path: '/' + child.path}">
                        {{ child.title.capitalize() }}
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
        default: (child: TocChild & {class?: string}) => any
    }>()

    const disabledPages = [
        "docs/api-reference",
        "docs/terraform/data-sources",
        "docs/terraform/guides",
        "docs/terraform/resources"
    ]

    interface TocChild {
        path: string;
        sidebarTitle?: string;
        title: string;
        children?: TocChild[];
    }

    const props = withDefaults(defineProps<{
        parent: {
            children: TocChild[]
        }
        depth?: number
        makeIndexNavigable?: boolean
    }>(), {
        makeIndexNavigable: true,
        depth: 0
    })

    const filteredChildren = computed(() => {
        return props.parent.children.map((child => ({...child, title: child.sidebarTitle ?? child.title})))
    })

    const openedDocs = ref<string[]>([]);
</script>

<style scoped lang="scss">
    .el-collapse {
        --el-collapse-header-font-size: 14px;

        > * {
            font-size: var(--el-collapse-header-font-size);
        }

        :deep(> .el-collapse-item) {
            > .el-collapse-item__header{
                padding: 0;
            }
            > button {
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

    .depth-0 {
        padding-left: 10px;
    }
    .depth-1 {
        padding-left: 20px;
    }
    .depth-2 {
        padding-left: 30px;
    }
    
</style>