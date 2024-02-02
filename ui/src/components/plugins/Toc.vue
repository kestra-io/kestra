<template>
    <div class="plugins-list">
        <el-collapse accordion>
            <template
                :key="plugin.title"
                v-for="(plugin) in sortedPlugins(plugins)"
            >
                <el-collapse-item
                    v-if="isVisible(plugin)"
                    :name="plugin.title"
                    :title="plugin.title"
                >
                    <ul class="toc-h3">
                        <li v-for="(types, namespace) in group(plugin, plugin.tasks)" :key="namespace">
                            <h6>{{ namespace }}</h6>
                            <ul class="toc-h4">
                                <li v-for="(classes, type) in types" :key="type+'-'+ namespace">
                                    <h6>{{ $filters.cap(type) }}</h6>
                                    <ul class="section-nav toc-h5">
                                        <li v-for="cls in classes" :key="cls">
                                            <router-link
                                                @click="$emit('routerChange')"
                                                :to="{name: 'plugins/view', params: {cls: namespace + '.' + cls}}"
                                            >
                                                <div class="icon">
                                                    <task-icon :only-icon="true" :cls="namespace + '.' + cls" :icons="icons" />
                                                </div>
                                                {{ cls }}
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
    import TaskIcon from "@kestra-io/ui-libs/src/components/misc/TaskIcon.vue";
    import {mapState} from "vuex";

    export default {
        emits: ["routerChange"],
        data() {
            return {
                offset: 0
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
            ...mapState("plugin", ["plugin", "plugins", "icons"]),
        },
        methods: {
            sortedPlugins(plugins) {
                return plugins
                    .sort((a, b) => {
                        const nameA = (a.manifest && a.title ? a.title.toLowerCase() : ""),
                              nameB = (b.manifest && b.title ? b.title.toLowerCase() : "");

                        return (nameA < nameB ? -1 : (nameA > nameB ? 1 : 0));
                    })
            },
            group(plugin) {
                return Object.keys(plugin)
                    .filter(r => r === "tasks" || r === "triggers" || r === "conditions")
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
                    .reduce((accumulator, value)  => {
                        accumulator[value.namespace] = accumulator[value.namespace] || {};
                        accumulator[value.namespace][value.type] = accumulator[value.namespace][value.type] || [];
                        accumulator[value.namespace][value.type].push(value.cls);

                        return accumulator;
                    }, Object.create(null))

            },
            isVisible(plugin) {
                return [...plugin.tasks, ...plugin.triggers, ...plugin.conditions].length > 0
            },
        }
    }
</script>

<style lang="scss">
    .plugins-list {
        ul {
            list-style: none;
            padding-inline-start: 0;
            margin-bottom: 0;
            font-size: var(--font-size-xs);
            margin-left: calc(var(--spacer) / 2);
        }

        h6, a {
            word-break: break-all;
        }

        .toc-h3 {
            .icon {
                width: var(--font-size-sm);
                height: var(--font-size-sm);
                display: inline-block;
                position: relative;
            }

            .toc-h4 {
                margin-left: var(--spacer);
                h6 {
                    font-size: var(--font-size-sm);
                    margin-bottom: calc(var(--spacer) / 3);
                }
            }
        }
    }
</style>
