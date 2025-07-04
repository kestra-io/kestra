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
                    :title="plugin.title.capitalize()"
                    :key="plugin.group"
                    :ref="`plugin-${plugin.group}`"
                >
                    <ul class="toc-h3">
                        <li v-for="(types, namespace) in group(plugin, plugin.tasks)" :key="namespace">
                            <h6>{{ namespace }}</h6>
                            <ul class="toc-h4">
                                <li v-for="(classes, type) in types" :key="type + '-' + namespace">
                                    <h6>{{ $filters.cap(type) }}</h6>
                                    <ul class="section-nav toc-h5">
                                        <li v-for="cls in classes" :key="cls">
                                            <router-link
                                                @click="$emit('routerChange'); handlePluginChange(namespace)"
                                                :to="{name: 'plugins/view', params: {cls: namespace + '.' + cls}}"
                                            >
                                                <div class="icon">
                                                    <task-icon
                                                        :only-icon="true"
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

<script>
    import {isEntryAPluginElementPredicate, TaskIcon} from "@kestra-io/ui-libs";
    import {mapStores} from "pinia";
    import {usePluginsStore} from "../../stores/plugins";

    export default {
        emits: ["routerChange"],
        data() {
            return {
                offset: 0,
                activeNames: [],
                searchInput: ""
            }
        },
        watch: {
            $route: {
                handler() {
                    this.plugins.forEach(plugin => {
                        if (Object.entries(plugin).some(([key, value]) => isEntryAPluginElementPredicate(key, value) && value.includes(this.$route.params.cls))) {
                            this.activeNames = [plugin.group]
                            localStorage.setItem("activePlugin", plugin.group);
                        }
                    })
                    this.scrollToActivePlugin();
                },
                immediate: true
            }
        },
        components: {
            TaskIcon
        },
        props: {
            plugins: {
                type: Array,
                required: true
            }
        },
        computed: {
            ...mapStores(usePluginsStore),
            countPlugin() {
                return this.plugins.flatMap(plugin => this.pluginElements(plugin)).length
            },
            pluginsList() {
                return this.plugins
                    // remove duplicate
                    .filter((plugin, index, self) => {
                        return index === self.findIndex((t) => (
                            t.title === plugin.title && t.group === plugin.group
                        ));
                    })
                    // find plugin that match search input
                    .filter(plugin => {
                        return plugin.title.toLowerCase().includes(this.searchInput.toLowerCase()) ||
                            this.pluginElements(plugin).some(element => element.toLowerCase().includes(this.searchInput.toLowerCase()))
                    })
                    // keep only task that match search input
                    .map(plugin => {
                        return {
                            ...plugin,
                            ...Object.fromEntries(Object.entries(plugin).filter(([key, value]) => isEntryAPluginElementPredicate(key, value))
                                .map(([elementType, elements]) => [elementType, elements.filter(element => element.toLowerCase().includes(this.searchInput.toLowerCase()))]))
                        }
                    })
            }
        },
        methods: {
            pluginElements(plugin) {
                return Object.entries(plugin).filter(([key, value]) => isEntryAPluginElementPredicate(key, value)).flatMap(([_, value]) => value)
            },
            scrollToActivePlugin() {
                const activePlugin = localStorage.getItem("activePlugin");
                if (activePlugin) {
                    // Use Vue's $refs to scroll to the specific plugin group
                    this.$nextTick(() => {
                        const pluginElement = this.$refs[`plugin-${activePlugin}`];
                        if (pluginElement && pluginElement[0]) {
                            pluginElement[0].$el.scrollIntoView({behavior: "smooth", block: "start"});
                        }
                    });
                }
            },
            // When user navigates to a different plugin, save the new plugin group to localStorage
            handlePluginChange(pluginGroup) {
                this.activeNames = [pluginGroup];
                localStorage.setItem("activePlugin", pluginGroup); // Save to localStorage
            },
            sortedPlugins(plugins) {
                return plugins
                    .sort((a, b) => {
                        const nameA = (a.title ? a.title.toLowerCase() : ""),
                              nameB = (b.title ? b.title.toLowerCase() : "");

                        return (nameA < nameB ? -1 : (nameA > nameB ? 1 : 0));
                    })
            },
            group(plugin) {
                return Object.entries(plugin)
                    .filter(([key, value]) => isEntryAPluginElementPredicate(key, value))
                    .flatMap(([type, value]) => {
                        return value.map(task => {
                            const namespace = task.substring(0, task.lastIndexOf("."));

                            return {
                                type,
                                namespace: namespace,
                                cls: task.substring(task.lastIndexOf(".") + 1)
                            };
                        });
                    })
                    .reduce((accumulator, value) => {
                        accumulator[value.namespace] = accumulator[value.namespace] || {};
                        accumulator[value.namespace][value.type] = accumulator[value.namespace][value.type] || [];
                        accumulator[value.namespace][value.type].push(value.cls);

                        return accumulator;
                    }, Object.create(null))

            },
            isVisible(plugin) {
                return this.pluginElements(plugin).length > 0
            },
        }
    }
</script>

<style lang="scss">
    .plugins-list {
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
</style>
