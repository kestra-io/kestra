<template>
    <div class="plugins-list">
        <el-collapse accordion >
            <el-collapse-item
                :title="plugin.manifest['X-Kestra-Title']"
                :name="plugin.manifest['X-Kestra-Title']"
                :key="plugin.manifest['X-Kestra-Title']"
                v-for="(plugin, index) in plugins"
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
                                                <task-icon :only-icon="true" :cls="namespace + '.' + cls" />
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
        </el-collapse>
    </div>
</template>

<script>
    import TaskIcon from "../plugins/TaskIcon";

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
        methods: {
            group(plugin) {
                return Object.keys(plugin)
                    .filter(r => r === "tasks" || r === "triggers" || r === "conditions")
                    .flatMap(type => {
                        return (plugin[type] === undefined ? {} : plugin[type])
                            .map(task => {
                                return {
                                    type: type,
                                    namepace: task.substring(0, task.lastIndexOf(".")),
                                    cls: task.substring(task.lastIndexOf(".") + 1)
                                };
                            })
                    })
                    .reduce((accumulator, value)  => {
                        accumulator[value.namepace] = accumulator[value.namepace] || {};
                        accumulator[value.namepace][value.type] = accumulator[value.namepace][value.type] || [];
                        accumulator[value.namepace][value.type].push(value.cls);

                        return accumulator;
                    }, Object.create(null));
            }
        }
    }
</script>

<style lang="scss">
    @use "sass:math";
    @use 'element-plus/theme-chalk/src/mixins/function' as *;
    @import "../../styles/_variable.scss";

    .plugins-list {
        font-size: $font-size-xs;

        ul {
            list-style: none;
            padding-inline-start: 0;
            margin-bottom: 0;
        }

        .toc-h3 {
            .icon {
                width: $font-size-sm;
                height: $font-size-sm;
                display: inline-block;
                position: relative;
            }

            .toc-h4 {
                margin-left: getCssVar('spacer');
                h6 {
                    font-size: $h6-font-size * 0.8;
                    margin-bottom: calc(getCssVar('spacer') / 3);
                }
            }
        }
    }
</style>
