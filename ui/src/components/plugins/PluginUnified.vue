<template>
    <div>
        <div class="p-4">
            <h1>
                <TaskIcon
                    class="icon"
                    :onlyIcon="true"
                    :cls="currentIcon"
                    :icons="icons"
                />
                {{ displayTitle }}
            </h1>
            <Markdown :source="plugin.description" />
            <Markdown :source="plugin.longDescription" />
        </div>

        <div v-if="showElements" class="elements-view">
            <div v-for="(elements, type) in elementsData" :key="type" class="section">
                <h2>{{ $t(type).capitalize() }}</h2>
                <div class="grid">
                    <RowLink
                        v-for="element in elements"
                        :key="element"
                        :icon="element"
                        :text="getShortName(element)"
                        :icons="pluginsStore.icons"
                        @click="openPlugin(element)"
                    />
                </div>
            </div>
        </div>
                
        <div v-else class="sub-sec">
            <div class="sub-grid">
                <RowLink
                    v-for="subgroupName in availableSubgroups"
                    :key="subgroupName"
                    :icon="getSubGroupIcon(subgroupName)"
                    :text="getSubgroupDisplayTitle(subgroupName)"
                    :icons="icons"
                    @click="openSubgroup(subgroupName)"
                />
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, onMounted, computed, watch} from "vue";
    import {isEntryAPluginElementPredicate, TaskIcon} from "@kestra-io/ui-libs";
    import RowLink from "../misc/RowLink.vue";
    import {usePluginsStore} from "../../stores/plugins";
    import Markdown from "../layout/Markdown.vue";
    import {getShortName, formatPluginTitle} from "../../utils/global";

    interface PluginElement {
        cls: string;
        deprecated: boolean;
    }

    interface Props {
        group: string;
        subgroup?: string;
        tenant?: string;
    }

    const props = withDefaults(defineProps<Props>(), {
        subgroup: undefined,
        tenant: undefined,
    });

    const emit = defineEmits<{
        "navigateToSubgroup": [subgroup: string];
        "navigateToElement": [cls: string];
    }>();

    const pluginsStore = usePluginsStore();

    const plugin = ref<any>({});
    const groupedElements = ref<Record<string, Record<string, string[]>>>({});
    const elementsData = ref<Record<string, string[]>>({});
    const icons = ref<Record<string, string>>({});
    const subgroupTitles = ref<Record<string, string>>({});

    const isSubgroupView = computed(() => !!props.subgroup);
    
    const availableSubgroups = computed(() => Object.keys(groupedElements.value));
    
    const showElements = computed(() => !!props.subgroup || Object.keys(groupedElements.value).length === 1);

    const displayTitle = computed(() => 
        props.subgroup 
            ? formatPluginTitle(subgroupTitles.value[getShortName(props.subgroup)]) ?? formatPluginTitle(getShortName(props.subgroup))
            : formatPluginTitle(plugin.value?.title)
    );

    const currentIcon = computed(() => props.subgroup ? getSubGroupIcon(props.subgroup) : props.group);

    const loadData = async () => {
        if (!props.group) return;
        groupedElements.value = {};
        elementsData.value = {};
        const plugins = await pluginsStore.listWithSubgroup({includeDeprecated: false});
        const matchingPlugin = plugins?.find((p: any) => p.group === props.group);
        if (!matchingPlugin) return;
        plugin.value = matchingPlugin;
        if (isSubgroupView.value) {
            await loadSubgroupData(matchingPlugin, plugins);
        } else {
            await loadGroupData(matchingPlugin, plugins);
        }
    };

    const loadGroupData = async (matchingPlugin: any, plugins: any[]) => {
        const groupParts = matchingPlugin.group.split(".");
        const subgroupTitleMap = plugins.reduce((map, p) => {
            if (p.group === props.group && p.subGroup && p.subGroup !== p.group) {
                const key = getShortName(p.subGroup);
                map[key] ??= p.title ?? p.subGroup;
            }
            return map;
        }, {} as Record<string, string>);
        const result = Object.entries(matchingPlugin).reduce((acc, [elementType, elements]) => {
            if (isEntryAPluginElementPredicate(elementType, elements)) {
                (elements as PluginElement[]).forEach(({cls}) => {
                    const parts = cls.split(".");
                    const subgroup = parts[groupParts.length] || "other";
                    if (!acc[subgroup]) acc[subgroup] = {};
                    if (!acc[subgroup][elementType]) acc[subgroup][elementType] = [];
                    acc[subgroup][elementType].push(cls);
                });
            }
            return acc;
        }, {} as Record<string, Record<string, string[]>>);
        groupedElements.value = result;
        subgroupTitles.value = subgroupTitleMap;
        if (Object.keys(result).length === 1) elementsData.value = Object.values(result)[0];
    };

    const loadSubgroupData = async (matchingPlugin: any, plugins: any[]) => {
        const subgroupPlugin = plugins.find(p => 
            p.group === props.group && 
            (p.subGroup === props.subgroup || p.subGroup?.endsWith(`.${props.subgroup}`))
        );
        if (subgroupPlugin?.title) subgroupTitles.value[getShortName(props.subgroup!)] = subgroupPlugin.title;
        const result = Object.entries(matchingPlugin).reduce((acc, [elementType, elements]) => {
            if (isEntryAPluginElementPredicate(elementType, elements)) {
                const filtered = (elements as PluginElement[]).filter(({cls}) => belongsToSubgroup(cls, matchingPlugin)).map(({cls}) => cls);
                if (filtered.length) acc[elementType] = filtered;
            }
            return acc;
        }, {} as Record<string, string[]>);
        elementsData.value = result;
    };

    const belongsToSubgroup = (cls: string, matchingPlugin: any): boolean => {
        if (!props.subgroup) return false;
        const parts = cls.split(".");
        const groupParts = props.group.split(".");
        return parts.length > groupParts.length && parts[groupParts.length] === props.subgroup ||
            matchingPlugin.subGroup === props.subgroup ||
            matchingPlugin.subGroup?.endsWith(`.${props.subgroup}`) ||
            cls.toLowerCase().includes(props.subgroup.toLowerCase());
    };

    const openSubgroup = (subgroup: string) => emit("navigateToSubgroup", subgroup);

    const openPlugin = (cls: string) => emit("navigateToElement", cls);

    const getSubGroupIcon = (subgroup: string) => {
        const keys = [`${props.group}.${subgroup}`, subgroup, plugin.value?.subGroup].filter(Boolean);
        return keys.find(key => icons.value[key]) || props.group;
    };

    const getSubgroupDisplayTitle = (subgroup: string) => formatPluginTitle(subgroupTitles.value[subgroup]) ?? formatPluginTitle(subgroup) ?? subgroup;

    onMounted(async () => {
        icons.value = await pluginsStore.groupIcons() ?? {};
        await loadData();
    });

    watch(() => [props.group, props.subgroup], async () => await loadData(), {deep: true});
</script>

<style scoped lang="scss">
    .section {
        h2 {
            color: var(--ks-content-primary);
            font-size: 1.20rem;
            font-weight: 600;
            padding: 0 1.25rem;
            margin-bottom: 1rem;
        }

        .grid {
            display: grid;
            grid-template-columns: 1fr;
            &:last-child {
                margin-bottom: 1.5rem;
            }
        }
    }

    .sub-sec {
        h2 {
            margin-bottom: 1rem;
            color: var(--ks-content-primary);
            font-size: 1.10rem;
            font-weight: 600;
        }

        .sub-grid {
            display: grid;
            grid-template-columns: 1fr;
        }
    }

    h1 {
        display: flex;
        align-items: center;
        gap: 1rem;
        margin-bottom: 1rem;
        color: var(--ks-content-primary);
        font-weight: 600;
        font-size: 24px;
        line-height: 36px;

        .icon {
            width: 40px;
            height: 40px;
            border-radius: 4px;
            flex-shrink: 0;
            opacity: 1;
        }
    }
</style>
