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
                    :hideAfter="0"
                >
                    <template #reference>
                        <el-button @click="copyLink(trigger)" size="small">
                            <TaskIcon :onlyIcon="true" :cls="trigger?.type" :icons="pluginsStore.icons" />
                        </el-button>
                    </template>
                    <template #default>
                        <TriggerVars :data="trigger" :execution="execution" @on-copy="copyLink(trigger)" />
                    </template>
                </el-popover>
            </template>
        </span>
    </div>
</template>
<script lang="ts" setup>
    import {computed} from "vue";
    import {useRoute} from "vue-router";
    import {usePluginsStore} from "../../stores/plugins";
    import Utils from "../../utils/utils";
    import TriggerVars from "./TriggerVars.vue";
    import {TaskIcon} from "@kestra-io/ui-libs";
    import {useI18n} from "vue-i18n";
    import {useToast} from "../../utils/toast";

    interface Flow {
        namespace: string;
        id: string;
        triggers?: Trigger[];
    }

    interface Execution {
        id: string;
        trigger?: Trigger;
    }

    interface Trigger {
        id: string;
        type: string;
        key?: string;
        disabled?: boolean;
        [key: string]: any;
    }

    const props = defineProps<{
        flow?: Flow;
        execution?: Execution;
        triggerId?: string;
    }>();

    const pluginsStore = usePluginsStore();
    const route = useRoute();

    const triggers = computed<Trigger[]>(() => {
        if (props.flow && props.flow.triggers) {
            return props.flow.triggers.filter(
                (trigger) => props.triggerId === undefined || props.triggerId === trigger.id
            );
        } else if (props.execution && props.execution.trigger) {
            return [props.execution.trigger];
        } else {
            return [];
        }
    });

    function uid(trigger: Trigger): string {
        return (props.flow ? props.flow.namespace + "-" + props.flow.id : props.execution?.id) + "-" + trigger.id;
    }

    const {t} = useI18n();
    const toast = useToast();

    async function copyLink(trigger: Trigger) {
        if (trigger?.type === "io.kestra.plugin.core.trigger.Webhook" && props.flow) {
            const tenant = route.params.tenant ? route.params.tenant + "/" : "";
            const url =
                new URL(window.location.href).origin +
                `/api/v1/${tenant}executions/webhook/${props.flow.namespace}/${props.flow.id}/${trigger.key}`;
            try {
                await Utils.copy(url);
                toast.success(t("webhook link copied"));
            } catch (error) {
                console.error(error);
            }
        }
    }
</script>

<style lang="scss" scoped>
    .trigger {
        max-width: 180px;
        overflow-x: auto;
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
