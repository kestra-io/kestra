<template>
    <div class="plugins-list">
        <b-card no-body :key="plugin.manifest['X-Kestra-Title']" v-for="plugin in plugins">
            <b-card-header header-tag="header" class="p-0" role="tab">
                <b-button block v-b-toggle.accordion-1 variant="light">{{ plugin.manifest['X-Kestra-Title'] }}</b-button>
            </b-card-header>
            <b-collapse id="accordion-1" visible accordion="my-accordion" role="tabpanel">
                <b-card-body>
                    <ul class="section-nav toc-h3">
                        <li v-for="(classes, namespace) in group(plugin.tasks)" :key="namespace">
                            {{ namespace }}
                            <ul>
                                <li v-for="cls in classes" :key="cls">
                                    <router-link
                                        v-on:click.native="$emit('routerChange')"
                                        :to="{ name: 'pluginView', params: {cls: namespace + '.' + cls}}"
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

        .card {
            button {
                border: 0;
                font-weight: bold;
                color: $gray-600;
            }
        }
        .card:not(:last-child) {
            margin-top: 0;
            border-bottom: 0;
        }
        .card-body {
            padding: $spacer/2;
        }
        ul {
            padding-left: 0;
            list-style: none;

            a {
                color: $gray-600;
                padding-left: $spacer;
            }
            ul {
                margin-bottom: $spacer / 2;
            }
        }
    }

</style>
