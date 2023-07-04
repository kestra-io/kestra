<template>
    <el-table v-if="this.flow" stripe table-layout="auto" :data="triggerData">
        <el-table-column prop="key" :label="$t('key')" />
        <el-table-column prop="value" :label="$t('value')">
            <template #default="scope">
                <span>{{ scope.row.value }}</span>
            </template>
        </el-table-column>
    </el-table>
</template>

<script>
    import {mapGetters} from "vuex";
    import RouteContext from "../../mixins/routeContext";

    export default {
        mixins: [RouteContext],
        created() {
            this.$store
                .dispatch("flow/loadFlow", {
                    id: this.flowId,
                    namespace: this.namespace,
                })
        },
        data() {
            return {
                flowId: this.$route.params.flowId,
                namespace: this.$route.params.namespace,
                triggerId: this.$route.params.id
            }
        },
        computed: {
            ...mapGetters("flow", ["flow"]),
            triggerData() {
                return Object.entries(
                    this.flow.triggers.filter(trigger => trigger.id === this.triggerId)[0]
                ).map(([key, value]) => {
                    return {
                        key,
                        value
                    };
                });
            },
            routeInfo() {
                return {
                    title: this.triggerId,
                    breadcrumb: [
                        {
                            label: this.$t("flows"),
                            link: {
                                name: "flows/list",
                                query: {
                                    namespace: this.namespace
                                }
                            }
                        },
                        {
                            label: `${this.namespace}.${this.flowId}`,
                            link: {
                                name: "flows/update",
                                params: {
                                    namespace: this.namespace,
                                    id: this.flowId
                                }
                            }
                        },
                        {
                            label: this.$t("triggers"),
                            link: {
                                name: "flows/update",
                                params: {
                                    namespace: this.namespace,
                                    id: this.flowId,
                                    tab: "triggers"
                                }
                            }
                        }
                    ]
                };
            }
        }
    };
</script>