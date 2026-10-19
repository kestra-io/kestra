<template>
    <NavBarAction
        v-if="isAllowedDelete"
        :icon="TrashCanOutline"
        @click="deleteExecution"
    >
        {{ $t("delete") }}
    </NavBarAction>
</template>

<script setup lang="ts">
    import {computed} from "vue"

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
    import NavBarAction from "../../../../layout/NavBarAction.vue"
    import {useExecutionDeletionDialog} from "../../../composables/useExecutionDeletionDialog"

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

    const {confirmExecutionDeletion} = useExecutionDeletionDialog()

    const deleteExecution = async () => {
        if (!props.execution) return

        let message = t("delete confirm", {name: props.execution.id})

        if (State.isRunning(props.execution.state.current)) {
            message += t("delete execution running")
        }

        const options = await confirmExecutionDeletion(message)
        if (!options) return

        await store.deleteExecution({id: props.execution.id, ...options})
        await router.push({name: "executions/list", params: {tenant: route.params.tenant}})
        toast.deleted(props.execution.id)
    }
</script>
