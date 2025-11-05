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
                <component :is="embedActiveTab || tab.disabled ? 'a' : 'router-link'" @click="embeddedTabChange(tab)" :to="embedActiveTab ? undefined : to(tab)" :data-test-id="tab.name">
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
    <section v-if="isEditorActiveTab || activeTab.component" ref="container" v-bind="$attrs" :class="{...containerClass, 'maximized': activeTab.maximized}">
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
            @go-to-detail="blueprintId => selectedBlueprintId = blueprintId"
            :embed="activeTab.props && activeTab.props.embed !== undefined ? activeTab.props.embed : true"
        />
    </section>
</template>

<script setup lang="ts">
    import { ref, computed, watch, onMounted, nextTick, useAttrs } from "vue";
    import { useRoute, useRouter } from "vue-router";
    import EnterpriseBadge from "./EnterpriseBadge.vue";
    import BlueprintDetail from "./flows/blueprints/BlueprintDetail.vue";

    interface Tab {
      name: string;
      title?: string;
      hidden?: boolean;
      disabled?: boolean;
      locked?: boolean;
      count?: number;
      maximized?: boolean;
      query?: Record<string, any>;
      props?: Record<string, any>;
      component?: any;
      ["v-on"]?: Record<string, (...args: any[]) => void>;
    }

    const props = defineProps<{
      tabs: Tab[];
      routeName?: string;
      top?: boolean;
      embedActiveTab?: string;
      namespace?: string | null;
      type?: string;
    }>();

    const emit = defineEmits<{
      (e: "changed", newTab: Tab): void;
    }>();

    const route = useRoute();
    const router = useRouter();
    const attrs = useAttrs();

    const activeName = ref<string | undefined>();
    const selectedBlueprintId = ref<string | undefined>();

    const activeTab = computed<Tab>(() => {
      return (
        props.tabs.find(
          (tab) => (props.embedActiveTab ?? route.params.tab) === tab.name
        ) || props.tabs[0]
      );
    });

    const containerClass = computed(() => getTabClasses(activeTab.value));

    const isEditorActiveTab = computed(() => {
    const TAB = activeTab.value?.name;
    const ROUTE = route.name as string;

    if (["flows/update", "flows/create"].includes(ROUTE)) return TAB === "edit";
    if (["namespaces/update", "namespaces/create"].includes(ROUTE))
        return TAB === "files";

    return false;
    });

    const attrsWithoutClass = computed(() => {
      return Object.fromEntries(Object.entries(attrs).filter(([key]) => key !== "class"));
    });

    const namespaceToForward = computed(() => {
      return activeTab.value?.props?.namespace ?? props.namespace;
    });

    function embeddedTabChange(tab: Tab) {
      emit("changed", tab);
    }

    function setActiveName() {
      activeName.value = activeTab.value?.name || "default";
    }

    function click(tabName: string) {
      const tab = props.tabs.find((t) => t.name === tabName);
      if (tab) router.push(to(tab));
    }

    function to(tab: Tab) {
      if (activeTab.value === tab) {
        setActiveName();
        return route;
      }

      return {
        name: props.routeName || (route.name as string),
        params: { ...route.params, tab: tab.name },
        query: { ...tab.query },
      };
    }

    function getTabClasses(tab: Tab) {
      const isEnterpriseTab = tab.locked;
      return {
        container: !isEnterpriseTab,
        "mt-4": !isEnterpriseTab,
        "px-0": isEnterpriseTab,
      };
    }

    watch(
      () => route.fullPath,
      () => setActiveName()
    );

    watch(
      () => activeTab.value,
      async () => {
        await nextTick();
        setActiveName();
      }
    );

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
