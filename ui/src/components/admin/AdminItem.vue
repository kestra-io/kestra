<template>
    <KsSideBarItem
        :title="t('admin')"
        :icon="CogOutline"
        :active="active"
        class="admin-item"
        @click="open"
    >
        <template v-if="configs?.version" #suffix>
            <KsTooltip placement="top">
                <template #content>
                    <div class="admin-item__tooltip">
                        <div>{{ t("version") }}: {{ configs.version }}</div>
                        <div v-if="configs.commitId">
                            {{ t("commit_id") }}:
                            <span class="admin-item__commit">{{ configs.commitId }}</span>
                        </div>
                        <div v-if="configs.commitDate">
                            {{ t("date") }}: {{ dateUtils.dateFilter(configs.commitDate) }}
                        </div>
                    </div>
                </template>
                <span class="admin-item__version">
                    v.{{ configs.version.split(".").slice(0, 2).join(".") }}
                </span>
            </KsTooltip>
        </template>
    </KsSideBarItem>
</template>

<script setup lang="ts">
    import {computed, onUnmounted, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter, type RouteLocationRaw} from "vue-router"
    import CogOutline from "vue-material-design-icons/CogOutline.vue"
    import {KsSideBarItem, KsTooltip, dateUtils} from "@kestra-io/design-system"
    import {useRouteTabsStore, type RouteTab} from "../../stores/routeTabs"
    import {useMiscStore} from "override/stores/misc"

    const props = defineProps<{
        tabs: RouteTab[]
        landingRoute?: RouteLocationRaw
    }>()

    const OWNER = Symbol("admin-tabs")

    const {t} = useI18n({useScope: "global"})
    const route = useRoute()
    const router = useRouter()
    const store = useRouteTabsStore()
    const miscStore = useMiscStore()

    const configs = computed(() => miscStore.configs)

    const active = computed(() => props.tabs
        .filter(tab => tab.route && !tab.header && !tab.excludeFromScope)
        .map(tab => router.resolve(tab.route!).path)
        .some(p => route.path === p || route.path.startsWith(p + "/")),
    )

    function open() {
        store.setTabs({ownerId: OWNER, tabs: props.tabs})
        const landing = props.landingRoute ?? props.tabs.find(t => !t.header && !t.disabled && t.route)?.route
        if (landing) router.push(landing)
    }

    watch(
        () => [route.path, props.tabs] as const,
        () => (active.value
            ? store.setTabs({ownerId: OWNER, tabs: props.tabs})
            : store.clearTabsIfOwner(OWNER)),
        {immediate: true, deep: true},
    )

    onUnmounted(() => store.clearTabsIfOwner(OWNER))
</script>

<style scoped lang="scss">
    .admin-item {
        margin: 0 var(--ks-spacing-4) var(--ks-spacing-1);
    }

    .admin-item__version {
        padding: var(--ks-spacing-1);
        border: 1px solid var(--ks-border-subtle);
        border-radius: var(--ks-radius-base);
        font-size: var(--ks-font-size-2xs);
        font-weight: var(--ks-font-weight-regular);
        color: var(--ks-text-secondary);
        line-height: 1;
        white-space: nowrap;
    }

    .admin-item__tooltip {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
        font-size: var(--ks-font-size-2xs);
        font-weight: var(--ks-font-weight-regular);
    }

    .admin-item__commit {
        color: var(--ks-text-link);
        font-family: var(--kel-font-family-monospace);
    }
</style>
