<template>
    <div class="trigger">
        <span v-for="trigger in triggers" :key="uid(trigger)" :id="uid(trigger)">
            <template v-if="trigger.disabled === undefined || trigger.disabled === false">
                <el-popover
                    placement="left"
                    :persistent="true"
                    :title="`${$t('trigger details')}: ${trigger ? trigger.id : ''}`"
                    width=""
                    transition=""
                    :hide-after="0"
                >
                    <template #reference>
                        <el-button>
                            <task-icon :only-icon="true" :cls="trigger?.type" :icons="icons" />
                        </el-button>
                    </template>
                    <template #default>
                        <trigger-vars :data="trigger" :execution="execution"/>
                    </template>
                </el-popover>
            </template>
        </span>
    </div>
</template>
<script>
    import TriggerVars from "./TriggerVars.vue";
    import {mapState} from "vuex";
    import TaskIcon from "@kestra-io/ui-libs/src/components/misc/TaskIcon.vue";

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
        },
        computed: {
            ...mapState("plugin", ["icons"]),
            triggers() {
                if (this.flow && this.flow.triggers) {
                    return this.flow.triggers
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
    .el-button {
        display: inline-flex !important;
        margin-right: calc(var(--spacer) / 4);
    }

    :deep(div.wrapper) {
        width: 20px;
        height: 20px;
    }
</style>
