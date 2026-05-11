<template>
    <KsTabs class="ks-router-tab" :class="{top}" v-model="activeName" type="box">
        <KsTabPane
            v-for="tab in visibleTabs"
            :key="tab.name ?? 'default'"
            :label="tab.title"
            :name="tab.name ?? 'default'"
            :disabled="tab.disabled"
        >
            <template #label>
                <component
                    :is="isEmbedded || tab.disabled ? 'a' : 'router-link'"
                    :to="isEmbedded ? undefined : toRoute(tab)"
                    @click="handleTabClick(tab)"
                >
                    <slot name="tab-label" :tab="tab">
                        <span class="ks-router-tab__label">
                            {{ tab.title }}
                            <KsBadge
                                v-if="tab.count !== undefined"
                                :value="tab.count"
                                type="primary"
                                class="ks-router-tab__badge"
                            />
                        </span>
                    </slot>
                </component>
            </template>
        </KsTabPane>
    </KsTabs>
    <section
        v-if="hasContent"
        ref="container"
        v-bind="$attrs"
        :class="{'maximized': activeTab.maximized, 'no-overflow': activeTab.noOverflow}"
    >
        <slot name="content" :activeTab="activeTab">
            <component
                v-if="activeTab.component"
                v-bind="activeTab.props"
                v-on="activeTab['v-on'] ?? {}"
                :is="activeTab.component"
            />
        </slot>
    </section>
</template>

<script setup lang="ts">
    import {ref, computed, watch, onMounted, nextTick, useSlots} from "vue"
    import {useRoute, type RouteLocationNormalizedLoaded} from "vue-router"
    import type {Component} from "vue"

    export interface RouterTab {
        name?: string
        title: string
        hidden?: boolean
        disabled?: boolean
        count?: number
        query?: Record<string, unknown>
        component?: Component
        props?: Record<string, unknown>
        "v-on"?: Record<string, unknown>
        maximized?: boolean
        noOverflow?: boolean
    }

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        tabs: RouterTab[]
        /**
         * Override the route name used for tab navigation.
         * Defaults to the current route name.
         */
        routeName?: string
        /**
         * Whether the tab bar sticks to the top of the viewport (box style).
         */
        top?: boolean
        /**
         * When defined, the component operates in embedded mode:
         * tab links are plain anchors and navigation is handled by the parent
         * via the `changed` event instead of vue-router.
         */
        embedActiveTab?: string
    }>(), {
        routeName: "",
        top: true,
        embedActiveTab: undefined,
    })

    const emit = defineEmits<{
        /**
         * Emitted when a tab is clicked in embedded mode.
         * The parent is responsible for updating `embedActiveTab`.
         */
        changed: [tab: RouterTab]
    }>()

    defineSlots<{
        /**
         * Scoped slot for customising the label of each tab.
         * Receives the tab object. Defaults to the title and count badge.
         */
        "tab-label"?(props: {tab: RouterTab}): unknown
        /**
         * Scoped slot for the content section rendered below the tab bar.
         * Receives the active tab. Falls back to rendering `activeTab.component`
         * when provided on the tab definition.
         */
        content?(props: {activeTab: RouterTab}): unknown
    }>()

    const slots = useSlots()

    // useRoute() returns the current route via inject; it may be undefined when no
    // router is installed (e.g. isolated unit-test environments).
    const route = useRoute() as RouteLocationNormalizedLoaded | undefined

    const activeName = ref<string | undefined>(undefined)

    const isEmbedded = computed(() => props.embedActiveTab !== undefined)

    const visibleTabs = computed(() => props.tabs.filter(t => !t.hidden))

    const activeTab = computed<RouterTab>(() => {
        const key = props.embedActiveTab ?? (route?.params?.tab as string | undefined)
        return props.tabs.find(t => t.name === key) ?? props.tabs[0]
    })

    const hasContent = computed(() => !!slots.content || !!activeTab.value?.component)

    const setActiveName = () => {
        activeName.value = activeTab.value?.name ?? "default"
    }

    const handleTabClick = (tab: RouterTab) => {
        if (isEmbedded.value) {
            emit("changed", tab)
        }
    }

    const toRoute = (tab: RouterTab) => {
        if (activeTab.value === tab) {
            setActiveName()
            return route
        }
        return {
            name: props.routeName || route?.name,
            params: {...route?.params, tab: tab.name},
            query: {...tab.query},
        }
    }

    if (route) {
        watch(route, () => setActiveName())
    }

    watch(activeTab, () => nextTick(() => setActiveName()))

    onMounted(() => setActiveName())
</script>

<style scoped lang="scss">
.maximized {
    margin: 0 !important;
    padding: 0;
    flex-grow: 1;
}

.no-overflow {
    overflow: hidden;
}

:deep(.kel-tabs) {
    .kel-tabs__item.is-disabled {
        &:after {
            top: 0;
            content: "";
            position: absolute;
            display: block;
            width: 100%;
            height: 100%;
            z-index: 1000;
        }

        a {
            color: var(--ks-content-inactive);
        }
    }
}

:deep(.kel-tabs__nav-next),
:deep(.kel-tabs__nav-prev) {
    &.is-disabled {
        display: none;
    }
}

.ks-router-tab__label {
    display: inline-flex;
    align-items: center;
    gap: 8px;
}

.ks-router-tab__badge {
    :deep(.kel-badge__content) {
        transform: translateY(-1px);
        position: static;
        border: none;
        margin-top: 0;
        vertical-align: middle;
    }
}
</style>
