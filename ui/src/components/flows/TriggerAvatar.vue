<template>
    <div class="trigger">
        <span v-for="trigger in triggers" :key="uid(trigger)" :id="uid(trigger)" class="trigger-icon">
            <template v-if="trigger.disabled === undefined || trigger.disabled === false">
                <KsPopover
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
                        <KsTaskIcon :onlyIcon="true" :cls="trigger?.type" :icons="pluginsStore.icons" />
                    </template>
                    <template #default>
                        <TriggerVars :data="trigger" :execution="execution" @on-copy="copyLink(trigger)" />
                    </template>
                </KsPopover>
            </template>
        </span>
    </div>
</template>
<script setup lang="ts">
    import {computed, ref, nextTick} from "vue"
    import {usePluginsStore} from "../../stores/plugins"
    import * as Utils from "../../utils/utils"
    import {webhookUrl, WEBHOOK_TRIGGER_TYPE} from "../../utils/webhook"
    import TriggerVars from "./TriggerVars.vue"
    import {KsTaskIcon} from "@kestra-io/design-system"
    import {useI18n} from "vue-i18n"
    import {useToast} from "../../utils/toast"
    import {Execution} from "../../stores/executions"

    interface Flow {
        namespace: string;
        id: string;
        triggers?: Trigger[];
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
    }>()

    const pluginsStore = usePluginsStore()

    const popoverRefs = ref<Map<string, any>>(new Map())

    const triggers = computed<Trigger[]>(() => {
        if (props.flow && props.flow.triggers) {
            return props.flow.triggers.filter(
                (trigger) => props.triggerId === undefined || props.triggerId === trigger.id,
            )
        } else if (props.execution && props.execution.trigger) {
            return [props.execution.trigger]
        } else {
            return []
        }
    })

    function uid(trigger: Trigger): string {
        return (props.flow ? props.flow.namespace + "-" + props.flow.id : props.execution?.id) + "-" + trigger.id
    }

    function setPopoverRef(el: any, trigger: Trigger) {
        if (el) {
            popoverRefs.value.set(uid(trigger), el)
        }
    }

    function handlePopoverShow() {
        nextTick(() => {
            popoverRefs.value.forEach((popover) => {
                if (popover?.popperRef?.popperInstanceRef) {
                    popover.popperRef.popperInstanceRef.update()
                }
            })
        })
    }

    const {t} = useI18n()
    const toast = useToast()

    async function copyLink(trigger: Trigger) {
        if (trigger?.type === WEBHOOK_TRIGGER_TYPE && trigger.key && props.flow) {
            const url = webhookUrl({namespace: props.flow.namespace, id: props.flow.id, key: trigger.key})
            try {
                await Utils.copy(url)
                toast.success(t("webhook link copied"))
            } catch (error) {
                console.error(error)
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
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: var(--ks-font-size-lg);
        height: var(--ks-font-size-lg);
        margin-right: var(--ks-spacing-1);
        cursor: default;
    }
</style>
