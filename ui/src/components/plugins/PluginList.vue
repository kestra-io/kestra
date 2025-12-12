<template>
    <div v-if="currentView !== 'documentation' || currentDocumentationPlugin" class="breadcrumb">
        <el-button
            v-if="navigationStack.length > 0"
            class="back-btn"
            @click="goBack"
            aria-label="Go back"
            :icon="ChevronLeft"
        />
        <el-breadcrumb separator="/">
            <el-breadcrumb-item>
                <a :class="{'fw-bold ps-2': navigationStack.length === 0}" href="#" @click.prevent="goToStep(-1)">{{ $t('plugins.names') }}</a>
            </el-breadcrumb-item>
            <el-breadcrumb-item
                v-for="(item, index) in navigationStack"
                :key="index"
                :class="{'is-active': index === navigationStack.length - 1}"
            >
                <a
                    v-if="index < navigationStack.length - 1"
                    href="#" 
                    @click.prevent="goToStep(index)"
                >
                    {{ item.title }}
                </a>
                <span v-else>{{ item.title }}</span>
            </el-breadcrumb-item>
        </el-breadcrumb>
        <SearchField 
            v-if="navigationStack.length === 0" 
            class="search-field" 
            :router="false" 
            @search="value => searchQuery = value" 
        />
    </div>

    <div v-if="currentView === 'list'" class="list" ref="listRef">
        <div
            v-for="plugin in sortedPlugins"
            :key="`${plugin.group}-${plugin.title}-${plugin.subGroup}`"
            class="item"
            @click.prevent="openGroup(plugin)"
        >
            <div class="content">
                <TaskIcon
                    class="icon"
                    :onlyIcon="true"
                    :cls="hasIcon(plugin.subGroup) ? plugin.subGroup : plugin.group"
                    :icons="icons"
                />
                <span class="name">{{ formatPluginTitle(plugin.title) }} </span>
            </div>
            <ChevronRight />
        </div>
    </div>

    <div v-else-if="currentView === 'group'" class="group-view" ref="groupRef">
        <PluginUnified
            :group="currentGroup"
            :subgroup="currentSubgroup"
            @navigate-to-subgroup="handleSubgroupNavigation"
            @navigate-to-element="handleElementNavigation"
        />
    </div>

    <div v-else-if="currentView === 'documentation'" :class="['doc-view', {'no-padding': !currentDocumentationPlugin}]" ref="docRef">
        <PluginDocumentation 
            :plugin="currentDocumentationPlugin"
        />
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted, watch} from "vue";
    import {TaskIcon, isEntryAPluginElementPredicate} from "@kestra-io/ui-libs";
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue";
    import ChevronLeft from "vue-material-design-icons/ChevronLeft.vue";
    import PluginUnified from "./PluginUnified.vue";
    import PluginDocumentation from "./PluginDocumentation.vue";
    import SearchField from "../layout/SearchField.vue";
    import {usePluginsStore} from "../../stores/plugins";
    import {useScrollMemory} from "../../composables/useScrollMemory";
    import {capitalize, formatPluginTitle} from "../../utils/global";
    import {useMiscStore} from "../../override/stores/misc";

    interface Props {
        plugins: any[];
    }

    interface NavigationItem {
        title: string;
        type: "group" | "subgroup" | "element";
        data: any;
    }

    const props = defineProps<Props>();

    const pluginsStore = usePluginsStore();

    const currentGroup = ref<string>("");
    const currentSubgroup = ref<string>();
    const searchQuery = ref<string>("");
    const icons = ref<Record<string, string>>({});
    const navigationStack = ref<NavigationItem[]>([]);
    const currentDocumentationPlugin = ref<any>(null);
    const currentView = ref<"list" | "group" | "documentation">("documentation");
    const listRef = ref<HTMLDivElement | null>(null);
    const groupRef = ref<HTMLDivElement | null>(null);
    const docRef = ref<HTMLDivElement | null>(null);
    const scrollKeyBase = "plugins:documentation";

    const listScrollKey = computed(() => `${scrollKeyBase}:list`);
    const groupScrollKey = computed(() => `${scrollKeyBase}:group`);
    const docScrollKey = computed(() => `${scrollKeyBase}:documentation`);

    useScrollMemory(listScrollKey, listRef);
    useScrollMemory(groupScrollKey, groupRef);
    useScrollMemory(docScrollKey, docRef);

    const getSimpleType = (item: string) => item.split(".").pop() || item;

    const pushNavigationItem = (title: string, type: NavigationItem["type"], data: any) => {
        navigationStack.value.push({title, type, data});
    };

    const getPluginElements = (plugin: any): string[] => 
        Object.entries(plugin ?? {})
            .filter(([elementType, elements]) => isEntryAPluginElementPredicate(elementType, elements))
            .flatMap(([, elements]) => 
                Array.isArray(elements) 
                    ? elements.filter(({deprecated}) => !deprecated).map(({cls}) => cls) 
                    : []
            );

    const getPluginDisplayName = (plugin: any): string => {
        return plugin?.manifest?.["X-Kestra-Title"];
    };

    const isPluginVisible = (plugin: any): boolean => {
        if (!plugin) return false;
        return getPluginElements(plugin).length > 0;
    };

    const removeDuplicatePlugins = (plugins: any[]) => {
        const seen = new Set();
        return plugins.filter(plugin => {
            const key = `${plugin.title || plugin.group}-${plugin.group}-${plugin.subGroup}`;
            if (seen.has(key)) return false;
            seen.add(key);
            return true;
        });
    };

    const resetToListView = () => {
        currentView.value = "list";
        navigationStack.value = [];
        currentGroup.value = "";
        currentSubgroup.value = undefined;
        currentDocumentationPlugin.value = null;
    };

    const basePlugins = computed(() => 
        removeDuplicatePlugins(
            (props.plugins ?? [])
                .filter(plugin => plugin && (plugin.group || plugin.subGroup))
        )
            .filter(isPluginVisible)
            .sort((a, b) => (getPluginDisplayName(a) ?? "").toLowerCase().localeCompare((getPluginDisplayName(b) ?? "").toLowerCase()))
    );

    const sortedPlugins = computed(() => {
        if (!searchQuery.value) return basePlugins.value;
        const query = searchQuery.value.toLowerCase();
        return basePlugins.value.filter(plugin => 
            (getPluginDisplayName(plugin) ?? "").toLowerCase().includes(query) ||
            (plugin.title ?? "").toLowerCase().includes(query)
        );
    });

    const loadPluginIcons = async () => {
        icons.value = await pluginsStore.groupIcons() ?? {};
    };

    const openGroup = (plugin: any) => {
        searchQuery.value = "";
        currentGroup.value = plugin.group;
        currentView.value = "group";
        currentDocumentationPlugin.value = null;

        if (plugin.subGroup && plugin.subGroup !== plugin.group) {
            currentSubgroup.value = plugin.subGroup;
            const groupPlugin = props.plugins.find(p => p.group === plugin.group && !p.subGroup);
            pushNavigationItem(formatPluginTitle(groupPlugin?.title) ?? capitalize(getSimpleType(plugin.group)), "group", {group: plugin.group});
            pushNavigationItem(formatPluginTitle(plugin.title) ?? formatPluginTitle(getSimpleType(plugin.subGroup)) ?? capitalize(getSimpleType(plugin.subGroup)), "subgroup", {subgroup: plugin.subGroup});
        } else {
            currentSubgroup.value = undefined;
            pushNavigationItem(formatPluginTitle(plugin.title) ?? capitalize(getSimpleType(plugin.group)), "group", plugin);
        }
    };

    const goToStep = (stepIndex: number) => {
        if (stepIndex === -1) return resetToListView();

        const targetStep = navigationStack.value[stepIndex];
        navigationStack.value = navigationStack.value.slice(0, stepIndex + 1);

        const actions = {
            group: () => {
                currentGroup.value = targetStep.data.group;
                currentSubgroup.value = undefined;
                currentView.value = "group";
                currentDocumentationPlugin.value = null;
            },
            subgroup: () => {
                currentSubgroup.value = targetStep.data.subgroup;
                currentView.value = "group";
                currentDocumentationPlugin.value = null;
            },
            element: () => {
                pluginsStore.load?.({cls: targetStep.data.cls}).then(pluginData => {
                    currentDocumentationPlugin.value = pluginData ? {cls: targetStep.data.cls, ...pluginData} : null;
                });
                currentView.value = "documentation";
            }
        };

        actions[targetStep.type]?.();
    };

    const goBack = () => {
        if (navigationStack.value.length > 1) {
            goToStep(navigationStack.value.length - 2);
        } else {
            goToStep(-1);
        }
    };

    const getSubgroupTitle = (group: string, subgroup: string): string => {
        const subgroupPlugin = props.plugins.find(p => 
            p.group === group && (p.subGroup === subgroup || p.subGroup?.endsWith(`.${subgroup}`))
        );
        return formatPluginTitle(subgroupPlugin?.title) ?? formatPluginTitle(subgroup) ?? subgroup;
    };

    const handleSubgroupNavigation = (subgroup: string) => {
        currentSubgroup.value = subgroup;
        pushNavigationItem(getSubgroupTitle(currentGroup.value, subgroup), "subgroup", {subgroup});
        currentView.value = "group";
        currentDocumentationPlugin.value = null;
    };

    const handleElementNavigation = async (cls: string) => {
        pushNavigationItem(getSimpleType(cls), "element", {cls});
        const pluginData = await pluginsStore.load({cls});
        currentDocumentationPlugin.value = pluginData ? {cls, ...pluginData} : null;
        currentView.value = "documentation";
    };

    const hasIcon = (cls: string) => !!icons.value?.[cls];

    const hash = computed(() => miscStore.configs?.pluginsHash ?? 0);
    const miscStore = useMiscStore();

    const navigateToEditorPlugin = async (editorPlugin: {cls: string, version?: string}) => {
        if (!editorPlugin?.cls) return;

        const pluginCls = editorPlugin.cls;
        const pluginVersion = editorPlugin.version;
        const matchingPlugin = props.plugins.find(plugin => getPluginElements(plugin).includes(pluginCls));

        if (!matchingPlugin) {
            currentDocumentationPlugin.value = editorPlugin;
            currentView.value = "documentation";
            return;
        }

        navigationStack.value = [];
        currentGroup.value = matchingPlugin.group;
        pushNavigationItem(formatPluginTitle(matchingPlugin.title) ?? capitalize(getSimpleType(matchingPlugin.group)), "group", matchingPlugin);

        const subgroupName = findSubgroupForPlugin(matchingPlugin, pluginCls);
        if (subgroupName) {
            currentSubgroup.value = subgroupName;
            pushNavigationItem(getSubgroupTitle(currentGroup.value, subgroupName), "subgroup", {subgroup: subgroupName});
        } else {
            currentSubgroup.value = undefined;
        }

        pushNavigationItem(getSimpleType(pluginCls), "element", {cls: pluginCls});
        currentView.value = "documentation";
        const pluginData = await pluginsStore.load({cls: pluginCls, version: pluginVersion, hash: hash.value});
        currentDocumentationPlugin.value = pluginData ? {cls: pluginCls, ...pluginData} : editorPlugin;
    };

    const findSubgroupForPlugin = (plugin: any, pluginCls: string) => {
        if (plugin?.subGroup && plugin.subGroup !== plugin.group) return plugin.subGroup;
        const parts = pluginCls.split(".");
        const possibleSubgroup = parts[parts.length - 2];
        return (parts.length >= 3 && possibleSubgroup && possibleSubgroup !== plugin?.group) ? possibleSubgroup : null;
    };

    watch(() => pluginsStore.editorPlugin, async (newPlugin) => {
        if (newPlugin?.cls) {
            await navigateToEditorPlugin(newPlugin);
        } else {
            currentDocumentationPlugin.value = null;
            currentView.value = "documentation";
            navigationStack.value = [];
            currentGroup.value = "";
            currentSubgroup.value = undefined;
        }
    }, {immediate: true, deep: true});

    onMounted(async () => {
        await loadPluginIcons();
    });
</script>

<style scoped lang="scss">
.breadcrumb {
    padding: 0.5rem 1rem;
    border-bottom: 1px solid var(--ks-border-primary);
    background-color: var(--ks-background-panel);
    position: sticky;
    top: 0;
    z-index: 10;
    min-height: 3.0625rem;
    display: flex;
    align-items: center;
    gap: 10px;

    .back-btn {
        background: none;
        border: none;
        cursor: pointer;
        padding: 0.5rem;

        :deep(svg) {
            font-size: 1.25rem;
            position: absolute;
            bottom: -0.10em;
        }
    }

    .search-field {
        width: 35%;
        margin-left: auto;

        :deep(.el-input__inner) {
            font-size: 14px;

            &::placeholder {
                color: var(--ks-content-tertiary) !important;
            }
        }
    }

    .el-breadcrumb {
        :deep(.el-breadcrumb__separator) {
            font-size: 1.375rem;
        }

        :deep(.el-breadcrumb__item .el-breadcrumb__inner) {
            text-transform: none !important;
        }

        :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
            font-weight: 700 !important;
        }
    }


}

.list {
    flex: 1;
    overflow-y: auto;

    .item {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 0.5rem 1.5rem;
        border-bottom: 1px solid var(--ks-border-primary);
        cursor: pointer;
        background-color: var(--ks-background-primary);

        .content {
            display: flex;
            align-items: center;
            gap: 0.75rem;
            flex: 1;

            .icon {
                height: 2.5rem;
                width: 2.5rem;
                flex-shrink: 0;
            }

            .name {
                color: var(--ks-content-primary);
                font-size: 1rem;
                line-height: 1.5;
            }
        }

        .chevron-right-icon {
            color: var(--ks-content-tertiary);
            font-size: 1.5rem;
        }
    }
}

.group-view {
    flex: 1;
    overflow-y: auto;
}

.doc-view {
    flex: 1;
    overflow-y: auto;
    padding: 1rem;

    &.no-padding {
        padding-top: 0;
    }
}

:deep(.markdown h3){
    margin-top: 0;
}
</style>
