<template>
    <div class="trigger">
        <span v-for="trigger in triggers" :key="uid(trigger)" :id="uid(trigger)">
            <template v-if="trigger.disabled === undefined || trigger.disabled === false">
                <el-popover
                    :ref="(el: any) => setPopoverRef(el, trigger)"
                    placement="left"
                    :persistent="true"
                    :title="`${$t('trigger details')}: ${trigger ? trigger.id : ''}`"
                    :width="500"
                    transition=""
                    :hideAfter="0"
                    @show="handlePopoverShow"
                >
                    <template #reference>
                        <TaskIcon :onlyIcon="true" :cls="trigger?.type" :icons="pluginsStore.icons" />
                    </template>
                    <template #default>
                        <TriggerVars :data="trigger" :execution="execution" @on-copy="copyLink(trigger)" />
                    </template>
                </el-popover>
            </template>
        </span>
    </div>
</template>
<script setup lang="ts">
    import {computed, ref, nextTick} from "vue";
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

    const popoverRefs = ref<Map<string, any>>(new Map());

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

    function setPopoverRef(el: any, trigger: Trigger) {
        if (el) {
            popoverRefs.value.set(uid(trigger), el);
        }
    }

    function handlePopoverShow() {
        nextTick(() => {
            popoverRefs.value.forEach((popover) => {
                if (popover?.popperRef?.popperInstanceRef) {
                    popover.popperRef.popperInstanceRef.update();
                }
            });
        });
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

<style scoped lang="scss">
    .trigger {
        max-width: 180px;
        display: flex;
        justify-content: center;
    }

    .trigger-icon {
        display: inline-flex !important;
        align-items: center;
        margin-right: .25rem;
        border: none;
        background-color: transparent;
        padding: 2px;
        cursor: default;
    }

    :deep(div.wrapper) {
        width: 20px;
        height: 20px;
    }
</style>
