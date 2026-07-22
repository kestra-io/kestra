<template>
    <NavBarAction
        v-if="isAllowedEdit"
        class="execution-edit-flow-button"
        :icon="Pencil"
        :to="editRoute"
    >
        {{ $t("edit flow") }}
    </NavBarAction>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useRoute} from "vue-router"

    import {Execution} from "../../../../../stores/executions"
    import {useAuthStore} from "override/stores/auth"

    import resource from "../../../../../models/resource"
    import action from "../../../../../models/action"

    import Pencil from "vue-material-design-icons/Pencil.vue"
    import NavBarAction from "../../../../layout/NavBarAction.vue"

    const props = defineProps<{ execution: Execution }>()

    const route = useRoute()

    const isAllowedEdit = computed(() => {
        return (
            props.execution &&
            useAuthStore().user?.isAllowed(
                resource.FLOW,
                action.UPDATE,
                props.execution.namespace,
            )
        )
    })

    const editRoute = computed(() => ({
        name: "flows/update",
        params: {
            namespace: route.params.namespace as string,
            id: route.params.flowId as string,
            tab: "edit",
            tenant: route.params.tenant as string,
        },
    }))
</script>
