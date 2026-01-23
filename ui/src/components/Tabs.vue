<template>
    <el-tabs class="router-link" :class="{top: top}" v-model="activeName" :type="type">
        <el-tab-pane
            v-for="tab in tabs.filter(t => !t.hidden)"
            :key="tab.name"
            :label="tab.title"
            :name="tab.name || 'default'"
            :disabled="tab.disabled"
        >
            <template #label>
                <component :is="embedActiveTab || tab.disabled ? 'a' : 'router-link'" @click="embeddedTabChange(tab)" :to="embedActiveTab ? undefined : to(tab)">
                    <el-tooltip v-if="tab.disabled && tab.props && tab.props.showTooltip" :content="$t('add-trigger-in-editor')" placement="top">
                        <span><strong>{{ tab.title }}</strong></span>
                    </el-tooltip>
                    <EnterpriseBadge :enable="tab.locked">
                        {{ tab.title }}
                        <el-badge :type="tab.count > 0 ? 'danger' : 'primary'" :value="tab.count" v-if="tab.count !== undefined" />
                    </EnterpriseBadge>
                </component>
            </template>
        </el-tab-pane>
    </el-tabs>
    <section v-if="isEditorActiveTab || activeTab.component" ref="container" v-bind="$attrs" :class="{...containerClass, 'maximized': activeTab.maximized, 'no-overflow': activeTab.noOverflow}">
        <BlueprintDetail
            v-if="selectedBlueprintId"
            :blueprintId="selectedBlueprintId"
            blueprintType="community"
            @back="selectedBlueprintId = undefined"
            :combinedView="true"
            :kind="activeTab.props.blueprintKind"
            :embed="activeTab.props && activeTab.props.embed !== undefined ? activeTab.props.embed : true"
        />
        <component
            v-else
            v-bind="{...activeTab.props, ...attrsWithoutClass}"
            v-on="activeTab['v-on'] ?? {}"
            ref="tabContent"
            :is="activeTab.component"
            :namespace="namespaceToForward"
            @go-to-detail="(blueprintId: string) => selectedBlueprintId = blueprintId"
            :embed="activeTab.props && activeTab.props.embed !== undefined ? activeTab.props.embed : true"
        />
    </section>
</template>

<script setup lang="ts">
    import {ref, computed, watch, onMounted, nextTick, useAttrs} from "vue";
    import {useRoute} from "vue-router";
    import EnterpriseBadge from "./EnterpriseBadge.vue";
    import BlueprintDetail from "../override/components/flows/blueprints/BlueprintDetail.vue";

    interface Tab {
        name?: string;
        title: string;
        hidden?: boolean;
        disabled?: boolean;
        props?: any;
        count?: number;
        locked?: boolean;
        query?: any;
        component?: any;
        maximized?: boolean;
        noOverflow?: boolean;
        "v-on"?: any;
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
        type?: string;
    }>(), {
        routeName: "",
        top: true,
        embedActiveTab: undefined,
        namespace: null,
        type: undefined
    });

    const emit = defineEmits<{
        /**
         * Especially useful when embedded since you need to handle the embedActiveTab prop change on the parent component.
         * @property {Object} newTab the new active tab
         */
        changed: [tab: Tab];
    }>();

    const attrs = useAttrs();
    const route = useRoute();

    const activeName = ref<string | undefined>(undefined);
    const selectedBlueprintId = ref<string | undefined>(undefined);

    const activeTab = computed(() => {
        return props.tabs.filter(tab => (props.embedActiveTab ?? route?.params?.tab) === tab.name)[0] || props.tabs[0];
    });

    const isEditorActiveTab = computed(() => {
        const TAB = activeTab.value.name;
        const ROUTE = route?.name as string;

        if (["flows/update", "flows/create"].includes(ROUTE)) {
            return TAB === "edit";
        } else if (["namespaces/update", "namespaces/create"].includes(ROUTE)) {
            if (TAB === "files") return true;
        }

        return false;
    });

    const attrsWithoutClass = computed(() => {
        return Object.fromEntries(
            Object.entries(attrs)
                .filter(([key]) => key !== "class")
        );
    });

    const namespaceToForward = computed(() => {
        return activeTab.value.props?.namespace ?? props.namespace;
        // in the special case of Namespace creation on Namespaces page, the tabs are loaded before the namespace creation
        // in this case this.props.namespace will be used
    });

    const containerClass = computed(() => getTabClasses(activeTab.value));

    const embeddedTabChange = (tab: Tab) => {
        emit("changed", tab);
    };

    const setActiveName = () => {
        activeName.value = activeTab.value.name || "default";
    };

    const to = (tab: Tab) => {
        if (activeTab.value === tab) {
            setActiveName();
            return route;
        } else {
            return {
                name: props.routeName || route?.name,
                params: {...route?.params, tab: tab.name},
                query: {...tab.query}
            };
        }
    };

    const getTabClasses = (tab: Tab) => {
        if (tab.locked) return {"px-0": true};
        return {"container": true, "mt-4": true};
    };

    if (route) {
        watch(route, () => {
            setActiveName();
        });
    }

    watch(activeTab, () => {
        nextTick(() => {
            setActiveName();
        });
    });

    onMounted(() => {
        setActiveName();
    });
</script>

<style scoped lang="scss">
section.container.mt-4:has(> section.empty) {
    margin: 0 !important;
    padding: 0 !important;
}

:deep(.el-tabs) {
    .el-tabs__item.is-disabled {
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

.maximized {
    margin: 0 !important;
    padding: 0;
    flex-grow: 1;
}

.no-overflow {
    overflow: hidden;
}

.editor-splitter {
    height: 100%;

    :deep(.el-splitter-panel) {
        display: flex;
        flex-direction: column;
    }
}

.sidebar {
    height: 100%;
    width: 100%;
}

:deep(.el-tabs__nav-next),
:deep(.el-tabs__nav-prev) {
    &.is-disabled {
        display: none;
    }
}
</style>