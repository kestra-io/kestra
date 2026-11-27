<template>
    <KsCollapse accordion flush v-model="openedDocs" :key="openedDocs">
        <template
            :key="child.title"
            v-for="child in filteredChildren"
        >
            <KsCollapseItem
                :name="child.path"
                v-if="child.children"
            >
                <template #title>
                    <slot
                        v-if="DISABLED_PAGES.includes(child.path) || !makeIndexNavigable"
                        name="label"
                        v-bind="child"
                        :class="`depth-${depth}`"
                    >
                        <span :class="`depth-${depth}`">
                            {{ child.title.capitalize() }}
                        </span>
                    </slot>
                    <slot v-else v-bind="child" :class="`depth-${depth}`">
                        <RouterLink :to="docRoute(child.path)" :class="`depth-${depth}`">
                            {{ child.title.capitalize() }}
                        </RouterLink>
                    </slot>
                </template>
                <RecursiveToc
                    :parent="{children: child.children}"
                    :makeIndexNavigable="makeIndexNavigable"
                    :depth="depth + 1"
                    :activePath="activePath"
                >
                    <template #default="subChild">
                        <slot v-bind="subChild" />
                    </template>
                    <template #label="subChild">
                        <slot name="label" v-bind="subChild" />
                    </template>
                </RecursiveToc>
            </KsCollapseItem>
            <div v-else>
                <slot v-bind="child" :class="`depth-${depth}`">
                    <RouterLink :to="docRoute(child.path)" :class="`depth-${depth}`">
                        {{ child.title.capitalize() }}
                    </RouterLink>
                </slot>
            </div>
        </template>
    </KsCollapse>
</template>

<script setup lang="ts">
    import {computed, ref, watch, type VNode} from "vue"
    import {useRoute} from "vue-router"
    import {DISABLED_PAGES} from "./docsUtils"

    defineOptions({
        name: "RecursiveToc",
    })

    defineSlots<{
        default: (child: TocChild & {class?: string}) => VNode[]
        label: (child: TocChild & {class?: string}) => VNode[]
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
        activePath?: string
    }>(), {
        makeIndexNavigable: true,
        depth: 0,
        activePath: undefined,
    })

    const route = useRoute()

    const filteredChildren = computed(() => {
        return props.parent.children.map((child => ({...child, title: child.sidebarTitle ?? child.title})))
    })

    const docRoute = (path: string) => ({
        path: route.params.tenant ? `/${route.params.tenant}/${path}` : `/${path}`,
    })

    const openedDocs = ref<string>("")

    watch(() => props.activePath, (activePath) => {
        const section = props.parent.children.find(({path, children}) =>
            children && (activePath === path || activePath?.startsWith(`${path}/`)),
        )
        if (section) openedDocs.value = section.path
    }, {immediate: true})
</script>

<style scoped>
    .kel-collapse {
        --kel-collapse-header-font-size: var(--ks-font-size-sm);
        --kel-collapse-header-height: auto;
        --kel-collapse-header-bg-color: transparent;
        --kel-collapse-content-bg-color: transparent;
        border-top: none;
        border-bottom: none;
        background-color: transparent;

        & > * {
            font-size: var(--kel-collapse-header-font-size);
        }

        & a {
            color: var(--ks-text-primary);
        }

        & a[class*="depth-"], & span[class*="depth-"] {
            display: block;
            width: 100%;
            padding: var(--ks-spacing-1) var(--ks-spacing-2);
            margin-bottom: 0.125rem;
            border-radius: var(--ks-radius-base);
            color: var(--ks-text-primary);
            text-decoration: none;

            &.depth-0 {
                padding-left: calc(var(--ks-spacing-2) + 0 * var(--ks-spacing-4));
                font-weight: var(--ks-font-weight-medium);
            }

            &.depth-1 {
                padding-left: calc(var(--ks-spacing-2) + 1 * var(--ks-spacing-4));
                font-size: var(--ks-font-size-xs);
                color: var(--ks-text-secondary);
            }

            &.depth-2 {
                padding-left: calc(var(--ks-spacing-2) + 2 * var(--ks-spacing-4));
                font-size: var(--ks-font-size-xs);
                color: var(--ks-text-secondary);
            }

            &.depth-3 {
                padding-left: calc(var(--ks-spacing-2) + 3 * var(--ks-spacing-4));
                font-size: var(--ks-font-size-xs);
                color: var(--ks-text-secondary);
            }

            &.depth-4 {
                padding-left: calc(var(--ks-spacing-2) + 4 * var(--ks-spacing-4));
                font-size: var(--ks-font-size-xs);
                color: var(--ks-text-secondary);
            }

            &.depth-5 {
                padding-left: calc(var(--ks-spacing-2) + 5 * var(--ks-spacing-4));
                font-size: var(--ks-font-size-xs);
                color: var(--ks-text-secondary);
            }

            &:hover {
                color: var(--ks-text-link);
                background-color: var(--ks-bg-hover);
            }

            &.router-link-exact-active {
                color: var(--ks-text-link);
                font-weight: var(--ks-font-weight-semibold);
                background-color: var(--ks-bg-hover);
            }
        }
    }
</style>