<template>
    <div class="plugins-list">
        <b-card class="accordion" no-body :key="plugin.manifest['X-Kestra-Title']" v-for="(plugin, index) in plugins">
            <b-card-header header-tag="header" class="p-0" role="tab">
                <b-button block v-b-toggle="plugin.manifest['X-Kestra-Title']" variant="light">
                    {{ plugin.manifest['X-Kestra-Title'] }}
                </b-button>
            </b-card-header>
            <b-collapse :id="plugin.manifest['X-Kestra-Title']" :visible="index === 0" accordion="my-accordion" role="tabpanel">
                <b-card-body>
                    <ul class="section-nav toc-h3">
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
                </b-card-body>
            </b-collapse>
        </b-card>
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
    @import "../../styles/_variable.scss";

    .plugins-list {
        font-size: $font-size-xs;

        .toc-h3 {
            .icon {
                width: $font-size-sm;
                height: $font-size-sm;
                display: inline-block;
                position: relative;
            }

            .toc-h4 {
                margin-left: $spacer;
                h6 {
                    font-size: $h6-font-size * 0.8;
                    margin-bottom: math.div($spacer, 3);
                }
            }
        }
    }
</style>
