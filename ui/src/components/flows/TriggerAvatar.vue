<template>
    <div class="trigger">
        <span v-for="trigger in triggers" :key="uid(trigger)" :id="uid(trigger)">
            <template v-if="trigger.disabled === undefined || trigger.disabled === false">
                <el-popover
                    placement="left"
                    :persistent="true"
                    :title="`${$t('trigger details')}: ${trigger ? trigger.id : ''}`"
                    :width="500"
                    transition=""
                    :hide-after="0"
                >
                    <template #reference>
                        <el-button @click="copyLink(trigger)" size="small">
                            <task-icon :only-icon="true" :cls="trigger?.type" :icons="icons" />
                        </el-button>
                    </template>
                    <template #default>
                        <trigger-vars :data="trigger" :execution="execution" @on-copy="copyLink(trigger)" />
                    </template>
                </el-popover>
            </template>
        </span>
    </div>
</template>
<script>
    import TriggerVars from "./TriggerVars.vue";
    import {mapState} from "vuex";
    import {TaskIcon} from "@kestra-io/ui-libs";

    export default {
        props: {
            flow: {
                type: Object,
                default: () => undefined,
            },
            execution: {
                type: Object,
                default: () => undefined,
            },
            triggerId: {
                type: String,
                default: null
            }
        },
        components: {
            TaskIcon,
            TriggerVars
        },
        methods: {
            uid(trigger) {
                return (this.flow ? this.flow.namespace + "-" + this.flow.id : this.execution.id) + "-" + trigger.id
            },
            name(trigger) {
                let split = trigger?.type.split(".");

                return split[split.length - 1].substr(0, 1).toUpperCase();
            },
            copyLink(trigger) {
                if (trigger?.type === "io.kestra.plugin.core.trigger.Webhook" && this.flow) {
                    const url = new URL(window.location.href).origin + `/api/v1/${this.$route.params.tenant ? this.$route.params.tenant +"/" : ""}executions/webhook/${this.flow.namespace}/${this.flow.id}/${trigger.key}`;

                    navigator.clipboard.writeText(url).then(() => {
                        this.$message({
                            message: this.$t("webhook link copied"),
                            type: "success"
                        });
                    });
                }
            }
        },
        computed: {
            ...mapState("plugin", ["icons"]),
            triggers() {
                if (this.flow && this.flow.triggers) {
                    return this.flow.triggers.filter(trigger => this.triggerId === null || this.triggerId === trigger.id)
                } else if (this.execution && this.execution.trigger) {
                    return [this.execution.trigger]
                } else {
                    return []
                }

            }
        }
    };
</script>

<style lang="scss" scoped>
    .trigger {
        max-width: 180px;
        overflow-x: auto;

        &::-webkit-scrollbar {
            width: 2px;
            height: 2px;
        }

        &::-webkit-scrollbar-track {
            background: var(--ks-background-card);
        }

        &::-webkit-scrollbar-thumb {
            background: var(--ks-button-background-primary);
            border-radius: 0px;
        }
    }

    .el-button {
        display: inline-flex !important;
        margin-right: .25rem;
    }

    :deep(div.wrapper) {
        width: 20px;
        height: 20px;
    }
</style>
