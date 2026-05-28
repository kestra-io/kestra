<template>
    <KsCollapse accordion v-model="openedDocs" :key="openedDocs">
        <template
            :key="child.title"
            v-for="child in filteredChildren"
        >
            <KsCollapseItem
                :name="child.path"
                v-if="child.children"
            >
                <template #title>
                    <span v-if="DISABLED_PAGES.includes(child.path) || !makeIndexNavigable" :class="`depth-${depth}`">
                        {{ child.title.capitalize() }}
                    </span>
                    <slot v-else v-bind="child" :class="`depth-${depth}`">
                        <RouterLink :to="{path: '/' + child.path}" :class="`depth-${depth}`">
                            {{ child.title.capitalize() }}
                        </RouterLink>
                    </slot>
                </template>
                <RecursiveToc
                    :parent="{children: child.children}"
                    :makeIndexNavigable="makeIndexNavigable"
                    :depth="depth + 1"
                >
                    <template #default="subChild">
                        <slot v-bind="subChild" />
                    </template>
                </RecursiveToc>
            </KsCollapseItem>
            <div v-else>
                <slot v-bind="child" :class="`depth-${depth}`">
                    <RouterLink :to="{path: '/' + child.path}">
                        {{ child.title.capitalize() }}
                    </RouterLink>
                </slot>
            </div>
        </template>
    </KsCollapse>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import {DISABLED_PAGES} from "./docsUtils"

    defineOptions({
        name: "RecursiveToc",
    })

    defineSlots<{
        default: (child: TocChild & {class?: string}) => any
    }>()


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
        depth: 0,
    })

    const filteredChildren = computed(() => {
        return props.parent.children.map((child => ({...child, title: child.sidebarTitle ?? child.title})))
    })

    const openedDocs = ref<string>("")
</script>

<style scoped lang="scss">
    .kel-collapse {
        --kel-collapse-header-font-size: var(--ks-font-size-sm);
        --kel-collapse-header-height: auto;
        border-top: none;
        border-bottom: none;

        > * {
            font-size: var(--kel-collapse-header-font-size);
        }

        :deep(> .kel-collapse-item) {
            > .kel-collapse-item__header {
                padding: 0;
                border-bottom: none;
                min-height: 32px;
                line-height: 1.2;
            }

            > button {
                padding: 0;
            }

            .kel-collapse-item__wrap {
                border-bottom: none;
            }

            a {
                color: var(--ks-text-primary);

                &.RouterLink-exact-active {
                    font-weight: 700;
                }
            }
        }

        :deep(.kel-collapse-item__content) {
            padding: 0;
        }

        :deep(.kel-collapse-item__arrow) {
            margin: 0 8px;
        }
    }

</style>