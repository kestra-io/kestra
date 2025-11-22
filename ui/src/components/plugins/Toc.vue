<template>
    <div class="plugins-list">
        <el-input
            class="p-2 bg-transparent search"
            :placeholder="$t('pluginPage.search', {count: countPlugin})"
            v-model="searchInput"
            clearable
        />
        <el-collapse accordion v-model="activeNames">
            <template v-for="(plugin) in sortedPlugins(pluginsList)" :key="plugin.title">
                <el-collapse-item
                    v-if="isVisible(plugin)"
                    :name="plugin.group"
                    :title="plugin.title?.capitalize()"
                    :key="plugin.group"
                    :ref="`plugin-${plugin.group}`"
                >
                    <ul class="toc-h3">
                        <li v-for="(types, namespace) in group(plugin)" :key="namespace">
                            <h6>{{ namespace }}</h6>
                            <ul class="toc-h4">
                                <li v-for="(classes, type) in types" :key="type + '-' + namespace">
                                    <h6>{{ capitalize(type) }}</h6>
                                    <ul class="section-nav toc-h5">
                                        <li v-for="cls in classes" :key="cls">
                                            <router-link
                                                @click="$emit('routerChange'); handlePluginChange(namespace)"
                                                :to="{name: 'plugins/view', params: {cls: namespace + '.' + cls}}"
                                            >
                                                <div class="icon">
                                                    <TaskIcon
                                                        :onlyIcon="true"
                                                        :cls="namespace + '.' + cls"
                                                        :icons="pluginsStore.icons"
                                                    />
                                                </div>
                                                <span
                                                    :class="$route.params.cls === (namespace + '.' + cls) ? 'selected mx-2' : 'mx-2'"
                                                >{{
                                                    cls
                                                }}</span>
                                            </router-link>
                                        </li>
                                    </ul>
                                </li>
                            </ul>
                        </li>
                    </ul>
                </el-collapse-item>
            </template>
        </el-collapse>
    </div>
</template>

<script setup lang="ts">
    import { ref, computed, watch, nextTick, getCurrentInstance } from 'vue';
    import { capitalize } from 'lodash';
    import { useRoute } from 'vue-router';
    import { isEntryAPluginElementPredicate, TaskIcon } from "@kestra-io/ui-libs";
    import { usePluginsStore } from "../../stores/plugins";

    // Define interfaces
    interface PluginElement {
        cls: string;
        deprecated?: boolean;
        [key: string]: any;
    }

    interface Plugin {
        title: string;
        group: string;
        [key: string]: any;
    }

    // Props
    const props = defineProps<{
        plugins: Plugin[];
    }>();

    // Emits
    const emit = defineEmits<{
        (e: 'routerChange'): void;
    }>();

    // Store and Route
    const pluginsStore = usePluginsStore();
    const route = useRoute();
    const instance = getCurrentInstance();

    // State
    const activeNames = ref<string[]>([]);
    const searchInput = ref("");

    // Methods
    const pluginElements = (plugin: Plugin): string[] => {
        return Object.entries(plugin)
            .filter(([key, value]) => isEntryAPluginElementPredicate(key, value))
            .flatMap(([_, value]) => (value as PluginElement[])
                .filter(({deprecated}) => !deprecated)
                .map(({cls}) => cls)
            );
    };

    const scrollToActivePlugin = () => {
        const activePlugin = localStorage.getItem("activePlugin");
        if (activePlugin) {
            nextTick(() => {
                const refs = instance?.proxy?.$refs as Record<string, any> | undefined;
                const pluginElement = refs?.[`plugin-${activePlugin}`];
                if (pluginElement && pluginElement[0]) {
                    pluginElement[0].$el.scrollIntoView({behavior: "smooth", block: "start"});
                }
            });
        }
    };

    const handlePluginChange = (pluginGroup: string) => {
        activeNames.value = [pluginGroup];
        localStorage.setItem("activePlugin", pluginGroup);
    };

    const sortedPlugins = (plugins: Plugin[]) => {
        return [...plugins].sort((a, b) => {
            const nameA = (a.title ? a.title.toLowerCase() : "");
            const nameB = (b.title ? b.title.toLowerCase() : "");

            return (nameA < nameB ? -1 : (nameA > nameB ? 1 : 0));
        });
    };

    const group = (plugin: Plugin) => {
        return Object.entries(plugin)
            .filter(([key, value]) => isEntryAPluginElementPredicate(key, value))
            .flatMap(([type, value]) => {
                return (value as PluginElement[])
                    .filter(({deprecated}) => !deprecated)
                    .map(({cls}) => {
                        const namespace = cls.substring(0, cls.lastIndexOf("."));

                        return {
                            type,
                            namespace: namespace,
                            cls: cls.substring(cls.lastIndexOf(".") + 1)
                        };
                    });
            })
            .reduce((accumulator, value) => {
                accumulator[value.namespace] = accumulator[value.namespace] || {};
                accumulator[value.namespace][value.type] = accumulator[value.namespace][value.type] || [];
                accumulator[value.namespace][value.type].push(value.cls);

                return accumulator;
            }, Object.create(null) as Record<string, Record<string, string[]>>);
    };

    const isVisible = (plugin: Plugin) => {
        return pluginElements(plugin).length > 0;
    };

    // Computed
    const countPlugin = computed(() => {
        return new Set(props.plugins.flatMap(plugin => pluginElements(plugin))).size;
    });

    const pluginsList = computed(() => {
        return props.plugins
            // remove duplicate
            .filter((plugin, index, self) => {
                return index === self.findIndex((t) => (
                    t.title === plugin.title && t.group === plugin.group
                ));
            })
            // find plugin that match search input
            .filter(plugin => {
                return plugin.title.toLowerCase().includes(searchInput.value.toLowerCase()) ||
                    pluginElements(plugin).some(element => element.toLowerCase().includes(searchInput.value.toLowerCase()))
            })
            // keep only task that match search input
            .map(plugin => {
                return {
                    ...plugin,
                    ...Object.fromEntries(
                        Object.entries(plugin)
                            .filter(([key, value]) => isEntryAPluginElementPredicate(key, value))
                            .map(([elementType, elements]) => [
                                elementType,
                                (elements as PluginElement[]).filter(({deprecated}) => !deprecated)
                                    .filter(({cls}) => cls.toLowerCase().includes(searchInput.value.toLowerCase()))
                            ])
                    )
                }
            })
    });

    // Watch
    watch(
        route,
        () => {
            props.plugins.forEach(plugin => {
                if (Object.entries(plugin).some(([key, value]) => 
                    isEntryAPluginElementPredicate(key, value) && 
                    (value as PluginElement[]).map(({cls}) => cls).includes(route.params.cls as string)
                )) {
                    activeNames.value = [plugin.group];
                    localStorage.setItem("activePlugin", plugin.group);
                }
            })
            scrollToActivePlugin();
        },
        { immediate: true }
    );
</script>

<style lang="scss" scoped>
    @import "@kestra-io/ui-libs/src/scss/variables";

    .plugins-list {
        display: flex;
        flex-direction: column;
        
        .search {
            flex-shrink: 0;
            background-color: var(--ks-background-panel);
            padding-bottom: 0.5rem;
        }
        
        .el-collapse {
            flex: 1;
        }
        
        &.enhance-readability {
            padding: 1.5rem;
            background-color: var(--bs-gray-100);
        }

        .el-collapse-item__header {
            font-size: 0.875rem;
        }

        ul {
            list-style: none;
            padding-inline-start: 0;
            margin-bottom: 0;
            font-size: var(--font-size-xs);
            margin-left: .5rem;
        }

        h6,
        a {
            word-break: break-all;
            color: var(--el-collapse-header-text-color);
        }

        .toc-h3 {
            .icon {
                width: var(--font-size-sm);
                height: var(--font-size-sm);
                display: inline-block;
                position: relative;
            }

            h6 {
                font-size: 1.1em;
            }

            .toc-h4 {
                margin-left: .5rem;

                h6 {
                    font-size: var(--font-size-sm);
                    margin-bottom: .5rem;
                }

                li {
                    margin-bottom: .5rem;
                }
            }
        }
    }

    .selected {
        color: var(--ks-content-link);
    }
    
    @media (max-width: 991px) {
        .plugins-list {
            .search {
                position: sticky;
                top: 0;
                z-index: 10;
            }
            
            .el-collapse {
                overflow-y: auto;
            }

            .el-collapse-item__header {
                font-size: 0.75rem;
            }

            ul {
                font-size: 0.6875rem;
                margin-left: .25rem;
            }

            .toc-h3 {
                .icon {
                    width: 0.875rem;
                    height: 0.875rem;
                }

                h6 {
                    font-size: 0.75rem;
                }

                .toc-h4 {
                    margin-left: .25rem;

                    h6 {
                        font-size: 0.6875rem;
                        margin-bottom: .25rem;
                    }

                    li {
                        margin-bottom: .25rem;
                    }
                }
            }
        }
    }

</style>
