<template>
    <KsRouterTab
        :tabs="tabs"
        :routeName="routeName"
        :top="top"
        :embedActiveTab="embedActiveTab"
        :class="containerClass"
        @changed="emit('changed', $event)"
    >
        <template #tab-label="{tab}">
            <KsTooltip
                v-if="tab.disabled && (tab as Tab).props?.showTooltip"
                :content="$t('add-trigger-in-editor')"
                placement="top"
            >
                <span><strong>{{ tab.title }}</strong></span>
            </KsTooltip>
            <EnterpriseBadge :enable="(tab as Tab).locked">
                <span class="tab-label-wrapper">
                    {{ tab.title }}
                    <KsBadge v-if="tab.count !== undefined" :value="tab.count" type="primary" class="inline-badge" />
                </span>
            </EnterpriseBadge>
        </template>
        <template #content="{activeTab: activeTabLocal}">
            <BlueprintDetail
                v-if="selectedBlueprintId"
                :blueprintId="selectedBlueprintId"
                blueprintType="community"
                @back="selectedBlueprintId = undefined"
                :combinedView="true"
                :kind="(activeTabLocal as Tab).props?.blueprintKind"
                :embed="(activeTabLocal as Tab).props?.embed ?? true"
            />
            <component
                v-else-if="isEditorActiveTab(activeTabLocal as Tab) || activeTabLocal.component"
                v-bind="{...(activeTabLocal as Tab).props, ...attrsWithoutClass}"
                v-on="(activeTabLocal as Tab)['v-on'] ?? {}"
                ref="tabContent"
                :is="activeTabLocal.component"
                :namespace="getNamespaceToForward(activeTabLocal as Tab)"
                @go-to-detail="(blueprintId: string) => selectedBlueprintId = blueprintId"
                :embed="(activeTabLocal as Tab).props?.embed ?? true"
            />
        </template>
    </KsRouterTab>
</template>

<script setup lang="ts">
    import {ref, computed, useAttrs} from "vue"
    import {useRoute} from "vue-router"
    import EnterpriseBadge from "./EnterpriseBadge.vue"
    import BlueprintDetail from "override/components/flows/blueprints/BlueprintDetail.vue"
    import type {RouterTab} from "@kestra-io/design-system"

    interface Tab extends RouterTab {
        locked?: boolean;
        props?: any;
    }

    const props = withDefaults(defineProps<{
        tabs: Tab[];
        routeName?: string;
        top?: boolean;
        /**
         * The active embedded tab. If this component is not embedded, keep it undefined.
         */
        embedActiveTab?: string;
        namespace?: string | null;
    }>(), {
        routeName: "",
        top: true,
        embedActiveTab: undefined,
        namespace: null,
    })

    const emit = defineEmits<{
        /**
         * Especially useful when embedded since you need to handle the embedActiveTab prop change on the parent component.
         * @property {Object} newTab the new active tab
         */
        changed: [tab: Tab];
    }>()

    const attrs = useAttrs()
    const route = useRoute()

    const selectedBlueprintId = ref<string | undefined>(undefined)

    const activeTab = computed<Tab>(() => {
        const key = props.embedActiveTab ?? (route?.params?.tab as string | undefined)
        return props.tabs.find(t => t.name === key) ?? props.tabs[0]
    })

    const isEditorActiveTab = (tab: Tab): boolean => {
        const TAB = tab.name
        const ROUTE = route?.name as string

        if (["flows/update", "flows/create"].includes(ROUTE)) {
            return TAB === "edit"
        } else if (["namespaces/update", "namespaces/create"].includes(ROUTE)) {
            if (TAB === "files") return true
        }

        return false
    }

    const attrsWithoutClass = computed(() => {
        return Object.fromEntries(
            Object.entries(attrs).filter(([key]) => key !== "class"),
        )
    })

    const getNamespaceToForward = (tab: Tab) => {
        return tab.props?.namespace ?? props.namespace
        // in the special case of Namespace creation on Namespaces page, the tabs are loaded before the namespace creation
        // in this case this.props.namespace will be used
    }

    const containerClass = computed(() => {
        if (activeTab.value.locked) return {"px-0": true}
        return {"container": true, "mt-4": true}
    })
</script>

<style scoped lang="scss">
    section.container.mt-4:has(> section.empty) {
        margin: 0 !important;
        padding: 0 !important;
    }

    .editor-splitter {
        height: 100%;

        :deep(.kel-splitter-panel) {
            display: flex;
            flex-direction: column;
        }
    }

    .sidebar {
        height: 100%;
        width: 100%;
    }

    .tab-label-wrapper {
        display: inline-flex;
        align-items: center;
        gap: 8px;
    }

    .inline-badge {
        :deep(.kel-badge__content) {
            transform: translateY(-1px);
            position: static;
            border: none;
            margin-top: 0;
            vertical-align: middle;
        }
    }
</style>
