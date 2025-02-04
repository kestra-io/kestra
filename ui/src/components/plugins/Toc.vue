<template>
    <div class="plugins-list">
        <el-input
            class="p-2 bg-transparent search"
            :placeholder="$t('pluginPage.search', {count: countPlugin})"
            v-model="searchInput"
            clearable
        />
        <el-collapse accordion v-model="activeNames">
            <template :key="plugin.title" v-for="(plugin) in sortedPlugins(pluginsList)">
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
                                                        :icons="icons"
                                                    />
                                                </div>
                                                <span
                                                    :class="$route.params.cls === (namespace + '.' + cls) ? 'selected mx-2' : 'mx-2'"
                                                >{{
                                                    cls }}</span>
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
    import {TaskIcon} from "@kestra-io/ui-libs";
    import {mapState} from "vuex";

    export default {
        emits: ["routerChange"],
        data() {
            return {
                offset: 0,
                activeNames: [],
                searchInput: ""
            }
        },
        mounted() {

            this.plugins.forEach(plugin => {
                if (plugin.tasks.includes(this.$route.params.cls)) {
                    this.activeNames = [plugin.group]
                    localStorage.setItem("activePlugin", plugin.group);
                }
            })
            this.scrollToActivePlugin();
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
            ...mapState("plugin", ["plugin", "icons"]),
            countPlugin() {
                return this.plugins.reduce((acc, plugin) => {
                    return acc + plugin.tasks.length + plugin.triggers.length + plugin.conditions.length + plugin.taskRunners.length
                }, 0)
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
                            plugin.tasks.some(task => task.toLowerCase().includes(this.searchInput.toLowerCase())) ||
                            plugin.triggers.some(trigger => trigger.toLowerCase().includes(this.searchInput.toLowerCase())) ||
                            plugin.conditions.some(condition => condition.toLowerCase().includes(this.searchInput.toLowerCase())) ||
                            plugin.taskRunners.some(taskRunner => taskRunner.toLowerCase().includes(this.searchInput.toLowerCase()))
                    })
                    // keep only task that match search input
                    .map(plugin => {
                        return {
                            ...plugin,
                            tasks: plugin.tasks.filter(task => task.toLowerCase().includes(this.searchInput.toLowerCase())),
                            triggers: plugin.triggers.filter(trigger => trigger.toLowerCase().includes(this.searchInput.toLowerCase())),
                            conditions: plugin.conditions.filter(condition => condition.toLowerCase().includes(this.searchInput.toLowerCase())),
                            taskRunners: plugin.taskRunners.filter(taskRunner => taskRunner.toLowerCase().includes(this.searchInput.toLowerCase()))
                        }
                    })
            }
        },
        methods: {

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
                return Object.keys(plugin)
                    .filter(r => r === "tasks" || r === "triggers" || r === "conditions" || r === "taskRunners")
                    .flatMap(type => {
                        return (plugin[type] === undefined ? {} : plugin[type])
                            .map(task => {
                                const namespace = task.substring(0, task.lastIndexOf("."));

                                return {
                                    type: type,
                                    namespace: namespace,
                                    cls: task.substring(task.lastIndexOf(".") + 1)
                                };
                            })
                    })
                    .reduce((accumulator, value) => {
                        accumulator[value.namespace] = accumulator[value.namespace] || {};
                        accumulator[value.namespace][value.type] = accumulator[value.namespace][value.type] || [];
                        accumulator[value.namespace][value.type].push(value.cls);

                        return accumulator;
                    }, Object.create(null))

            },
            isVisible(plugin) {
                return [...plugin.tasks, ...plugin.triggers, ...plugin.conditions, ...plugin.taskRunners].length > 0
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

    &::-webkit-scrollbar {
        width: 2px;
    }

    &::-webkit-scrollbar-track {
        -webkit-border-radius: 10px;
    }

    &::-webkit-scrollbar-thumb {
        -webkit-border-radius: 10px;
        background: var(--bs-gray-600);
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
