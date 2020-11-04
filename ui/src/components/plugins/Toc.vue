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
                        <li v-for="(classes, namespace) in group(plugin.tasks)" :key="namespace">
                            {{ namespace }}
                            <ul>
                                <li v-for="cls in classes" :key="cls">
                                    <router-link
                                        @click.native="$emit('routerChange')"
                                        :to="{name: 'pluginView', params: {cls: namespace + '.' + cls}}"
                                    >
                                        {{ cls }}
                                    </router-link>
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
    export default {
        data() {
            return {
                offset: 0
            }
        },
        props: {
            plugins: {
                type: Array,
                required: true
            }
        },
        methods: {
            group(tasks) {
                return tasks === undefined ? {} : tasks
                    .map(task => {
                        return {
                            namepace: task.substring(0, task.lastIndexOf(".")),
                            cls: task.substring(task.lastIndexOf(".") + 1)
                        };
                    })
                    .reduce((accumulator, value)  => {
                        accumulator[value.namepace] = accumulator[value.namepace] || [];
                        accumulator[value.namepace].push(value.cls);
                        return accumulator;
                    }, Object.create(null));
            }
        }
    }
</script>

<style lang="scss">
    @import "../../styles/_variable.scss";

    .plugins-list {
        font-size: $font-size-xs;
    }

</style>
