<template>
    <span class="d-none" />
</template>

<script setup lang="ts">
    import {KsNotification} from "@kestra-io/design-system"
    import {pageFromRoute} from "../utils/eventsRouter"
    import {h, onUnmounted, watch, computed, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute} from "vue-router"
    import type {ProblemFieldError} from "@kestra-io/kestra-sdk"
    import ErrorToastContainer from "./ErrorToastContainer.vue"
    import {useApiStore} from "../stores/api"
    import {problemDetail, problemTitle, type ToastMessage} from "../utils/problem"

    interface ErrorEvent {
        type: string
        error: {
            message: string
            /** Stable, low-cardinality dimension for error analytics, unlike free-text prose. */
            problemType?: string
            errors: readonly ProblemFieldError[]
            response?: {status?: number}
            request?: {url: string; method: string}
        }
        page: any
    }

    const props = withDefaults(defineProps<{
        message: ToastMessage
        noAutoHide?: boolean
    }>(), {
        noAutoHide: false,
    })

    const route = useRoute()
    const {t, te} = useI18n()
    const apiStore = useApiStore()
    const notifications = ref<any>()

    const close = () => {
        if (notifications.value) {
            notifications.value.close()
            notifications.value = undefined
        }
    }

    // The problem's kind, localized. Independent of `detail`, so a message containing a colon is safe.
    const title = computed(() => props.message.title ?? problemTitle(props.message.problem, t, te))

    const detail = computed(() => props.message.content ?? problemDetail(props.message.problem, t, te))

    const items = computed<readonly ProblemFieldError[]>(() => props.message.problem?.errors ?? [])

    // Only meaningful on a server error, where it is the sole link between what the user saw and the log
    // entry holding the real cause.
    const traceId = computed(() => {
        const problem = props.message.problem
        return problem && problem.status >= 500 ? problem.traceId : undefined
    })

    const isLargeNotification = computed(() => items.value.length > 0 || detail.value.length > 100)

    watch(route, () => {
        close()
    })

    // Only an error toast is a telemetry event: a success or info toast reusing this
    // component would otherwise be captured as an error.
    const isErrorVariant = computed(() =>
        props.message.variant === undefined || props.message.variant === "error")

    const showNotification = () => {
        close()

        if (isErrorVariant.value) {
            const error: ErrorEvent = {
                type: "ERROR",
                error: {
                    message: title.value,
                    problemType: props.message.problem?.type,
                    errors: items.value,
                },
                page: pageFromRoute(route),
            }

            const status = props.message.status ?? props.message.problem?.status
            if (status !== undefined) {
                error.error.response = {status}
            }
            if (props.message.request) {
                error.error.request = props.message.request
            }

            apiStore.events(error)
        }

        notifications.value = KsNotification({
            title: title.value,
            message: h(ErrorToastContainer, {
                detail: detail.value,
                items: items.value,
                traceId: traceId.value,
                onClose: () => close(),
            }),
            position: "bottom-right",
            type: props.message.variant || "error",
            duration: 0,
            dangerouslyUseHTMLString: true,
            customClass: isLargeNotification.value ? "error-notification kel-notification__large" : "error-notification",
        })
    }

    watch(() => props.message, showNotification, {immediate: true})

    onUnmounted(() => close())
</script>

<style lang="scss" scoped>
    .error-notification {
        max-height: 90svh;

        .kel-notification__title {
            max-width: calc(100% - 15ch);
        }

        .slack-on-error {
            top: calc(18px + 0.5rem);
            right: calc(15px + 2rem);
            transform: translateY(-50%);
            gap: .5rem;
        }

        .kel-notification__content {
            overflow-y: auto;
            max-height: 100%;
        }
    }
</style>
