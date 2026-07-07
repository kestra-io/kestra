<template>
    <KsPopover
        v-model:visible="visible"
        trigger="click"
        placement="bottom-end"
        :width="376"
        :showArrow="false"
        :popperStyle="POPPER_STYLE"
    >
        <template #reference>
            <KsButton
                class="see-timeline"
                :class="{'is-active': visible}"
                :icon="ArrowExpand"
                link
            >
                {{ t("see timeline") }}
            </KsButton>
        </template>

        <div class="run-timeline">
            <div class="run-timeline__header">
                <span class="run-timeline__title">{{ t("run timeline") }}</span>
                <KsIconButton :tooltip="t('close')" placement="top" @click="visible = false">
                    <Close />
                </KsIconButton>
            </div>

            <div class="run-timeline__body">
                <div
                    v-for="(activity, index) in histories"
                    :key="index"
                    class="run-timeline__item"
                >
                    <span class="run-timeline__dot" />
                    <span class="run-timeline__date">{{ formatDate(activity.date) }}</span>
                    <KsExecutionStatus :status="activity.state" size="small" />
                </div>
            </div>
        </div>
    </KsPopover>
</template>

<script setup lang="ts">
    import {ref} from "vue"
    import {useI18n} from "vue-i18n"

    import moment from "moment"
    import {KsExecutionStatus} from "@kestra-io/design-system"

    import ArrowExpand from "vue-material-design-icons/ArrowExpand.vue"
    import Close from "vue-material-design-icons/Close.vue"

    import {Histories} from "../../../../stores/executions"

    defineProps<{histories: Histories[]}>()

    const {t} = useI18n({useScope: "global"})

    const POPPER_STYLE = {
        padding: "0",
        overflow: "hidden",
        borderRadius: "0.875rem",
        background: "var(--ks-bg-elevated)",
        boxShadow: "0px 8px 24px 0px var(--ks-shadow-elevated)",
    }

    const visible = ref(false)

    const formatDate = (date: string) => moment(date).format("YYYY-MM-DD HH:mm:ss.SSS")
</script>

<style scoped lang="scss">
    .see-timeline {
        gap: var(--ks-spacing-1);
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        border: 1px solid transparent;
        border-radius: var(--ks-radius-base);
        color: var(--ks-text-secondary);
        font-weight: 600;
        font-size: var(--ks-font-size-sm);

        &:hover {
            color: var(--ks-text-primary);
        }

        &.is-active,
        &.is-active:hover {
            color: var(--ks-text-primary);
            background: var(--ks-btn-secondary-bg-active);
            border-color: var(--ks-btn-secondary-border-active);
        }
    }

    .run-timeline {
        display: flex;
        flex-direction: column;

        &__header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: var(--ks-spacing-5) var(--ks-spacing-4);
            padding-bottom: 0;
        }

        &__title {
            font-weight: 600;
            color: var(--ks-text-primary);
            font-size: var(--ks-font-size-lg);
        }

        &__body {
            display: flex;
            flex-direction: column;
            gap: var(--ks-spacing-5);
            padding: var(--ks-spacing-5) var(--ks-spacing-4);
            padding-left: 2rem;
            max-height: 24rem;
            overflow-y: auto;
        }

        &__item {
            position: relative;
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-4);

            &:not(:last-child)::after {
                content: "";
                position: absolute;
                left: calc(var(--ks-spacing-1) / 2);
                transform: translateX(-50%);
                top: calc(100% + var(--ks-spacing-1));
                height: var(--ks-spacing-4);
                border-left: 1px dashed var(--ks-text-primary);
            }
        }

        &__dot {
            flex-shrink: 0;
            width: var(--ks-spacing-1);
            height: var(--ks-spacing-1);
            border-radius: 50%;
            background: var(--ks-text-primary);
        }

        &__date {
            color: var(--ks-text-secondary);
            font-size: var(--ks-font-size-sm);
            font-variant-numeric: tabular-nums;
        }
    }
</style>
