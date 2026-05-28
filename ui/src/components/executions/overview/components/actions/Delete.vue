<template>
    <KsButton
        v-if="isAllowedDelete"
        :icon="TrashCanOutline"
        @click="deleteExecution"
    >
        {{ $t("delete") }}
    </KsButton>
</template>

<script setup lang="ts">
    import {computed, ref, h} from "vue"

    import {KsMessageBox, KsCheckbox} from "@kestra-io/design-system"

    import {
        Execution,
        useExecutionsStore,
    } from "../../../../../stores/executions"
    const store = useExecutionsStore()
    import {useAuthStore} from "override/stores/auth"

    import resource from "../../../../../models/resource"
    import action from "../../../../../models/action"

    import {State} from "@kestra-io/design-system"

    import {useToast} from "../../../../../utils/toast"
    const toast = useToast()

    import {useRouter, useRoute} from "vue-router"
    const router = useRouter()
    const route = useRoute()

    import {useI18n} from "vue-i18n"
    const {t} = useI18n({useScope: "global"})

    import TrashCanOutline from "vue-material-design-icons/TrashCanOutline.vue"

    const props = defineProps<{ execution: Execution }>()

    const isAllowedDelete = computed(() => {
        return (
            props.execution &&
            useAuthStore().user?.isAllowed(
                resource.EXECUTION,
                action.DELETE,
                props.execution.namespace,
            )
        )
    })

    const deleteExecution = () => {
        if (!props.execution) return

        let message = t("delete confirm", {name: props.execution.id})

        if (State.isRunning(props.execution.state.current)) {
            message += t("delete execution running")
        }

        const deleteLogs = ref(true)
        const deleteMetrics = ref(true)
        const deleteStorage = ref(true)

        KsMessageBox({
            boxType: "confirm",
            title: t("confirmation"),
            showCancelButton: true,
            customStyle: {minWidth: "600px"},
            callback: (value: string) => {
                if (value === "confirm") {
                    return store
                        .deleteExecution({
                            ...props.execution,
                            deleteLogs: deleteLogs.value,
                            deleteMetrics: deleteMetrics.value,
                            deleteStorage: deleteStorage.value,
                        })
                        .then(() => {
                            return router.push({
                                name: "executions/list",
                                params: {
                                    tenant: route.params.tenant,
                                },
                            })
                        })
                        .then(() => {
                            toast.deleted(props.execution.id)
                        })
                }
            },
            message: () =>
                h("div", null, [
                    h("p", {class: "pb-3"}, [h("span", {innerHTML: message})]),
                    h(KsCheckbox, {
                        modelValue: deleteLogs.value,
                        label: t("execution_deletion.logs"),
                        "onUpdate:modelValue": (val) =>
                            (deleteLogs.value = Boolean(val)),
                    }),
                    h(KsCheckbox, {
                        modelValue: deleteMetrics.value,
                        label: t("execution_deletion.metrics"),
                        "onUpdate:modelValue": (val) =>
                            (deleteMetrics.value = Boolean(val)),
                    }),
                    h(KsCheckbox, {
                        modelValue: deleteStorage.value,
                        label: t("execution_deletion.storage"),
                        "onUpdate:modelValue": (val) =>
                            (deleteStorage.value = Boolean(val)),
                    }),
                ]),
        })
    }
</script>
