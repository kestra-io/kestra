<template>
    <KsCard
        shadow="never"
        class="operation"
        role="button"
        tabindex="0"
        data-test="operation-card"
        @click="$emit('click')"
        @keydown.enter="$emit('click')"
        @keydown.space.prevent="$emit('click')"
    >
        <div class="body">
            <div class="head">
                <KsIcon class="spinner" aria-hidden="true">
                    <Loading />
                </KsIcon>
                <p class="title">{{ title }}</p>
            </div>
            <KsProgress
                v-if="percentage !== null"
                :percentage="percentage"
                :color="progressColor(notification)"
                :showText="false"
                :strokeWidth="4"
            />
            <p class="meta">{{ meta }}</p>
        </div>
    </KsCard>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import {useNow} from "@vueuse/core"
    import {durationUtils} from "@kestra-io/design-system"
    import Loading from "vue-material-design-icons/Loading.vue"
    import {humanizeNumber} from "../../utils/filters"
    import type {Notification} from "../../stores/notifications"
    import {progressColor, progressPercentage} from "../../utils/notificationProgress"

    const props = defineProps<{notification: Notification}>()
    defineEmits<{click: []}>()

    const {t, te} = useI18n()
    const now = useNow({interval: 1000})

    const formatCount = (value: number) => humanizeNumber(String(value))

    const title = computed(() => {
        const {asyncOperationType, totalItems} = props.notification
        const key = `notifications.operation.running.${asyncOperationType}`

        return totalItems != null && te(key)
            ? t(key, {count: formatCount(totalItems)}, totalItems)
            : props.notification.title
    })

    const percentage = computed(() => progressPercentage(props.notification))

    const meta = computed(() => {
        const {totalItems, succeededItems, failedItems, createdDate} = props.notification
        const processed = (succeededItems ?? 0) + (failedItems ?? 0)
        const elapsed = Math.max(0, Math.floor((now.value.getTime() - Date.parse(createdDate)) / 1000))

        return [
            totalItems != null && t("notifications.operation.processed", {count: formatCount(processed)}),
            failedItems && t("notifications.operation.rejected", {count: formatCount(failedItems)}),
            t("notifications.operation.elapsed", {duration: durationUtils.humanDuration(elapsed)}),
        ]
            .filter(Boolean)
            .join(" · ")
    })
</script>

<style scoped lang="scss">
    .operation {
        cursor: pointer;
        transition: border-color 0.15s ease;

        &:hover,
        &:focus-visible {
            border-color: var(--ks-border-focus);
        }

        .body {
            display: flex;
            flex-direction: column;
            gap: var(--ks-spacing-2);
        }

        .head {
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-2);

            .spinner {
                flex-shrink: 0;
                color: var(--ks-icon-active);
                animation: spin 1s linear infinite;

                @media (prefers-reduced-motion: reduce) {
                    animation: none;
                }
            }

            .title {
                margin: 0;
                font-size: var(--ks-font-size-sm);
                font-weight: var(--ks-font-weight-semibold);
                color: var(--ks-text-primary);
            }
        }

        .meta {
            margin: 0;
            font-size: var(--ks-font-size-2xs);
            color: var(--ks-text-secondary);
            font-variant-numeric: tabular-nums;
        }
    }

    @keyframes spin {
        to {
            transform: rotate(360deg);
        }
    }
</style>
