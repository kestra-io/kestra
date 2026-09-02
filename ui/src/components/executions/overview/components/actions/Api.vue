<template>
    <NavBarAction
        v-if="isAllowedEdit"
        :icon="Api"
        @click="downloadApi"
    >
        {{ $t("api") }}
    </NavBarAction>
</template>

<script setup lang="ts">
    import {computed} from "vue"

    import {apiUrl} from "override/utils/route"

    import * as Utils from "../../../../../utils/utils"

    import {Execution} from "../../../../../stores/executions"
    import {useAuthStore} from "override/stores/auth"

    import resource from "../../../../../models/resource"
    import action from "../../../../../models/action"

    import Api from "vue-material-design-icons/Api.vue"
    import NavBarAction from "../../../../layout/NavBarAction.vue"

    const props = defineProps<{ execution: Execution }>()

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

    const downloadApi = () => {
        Utils.downloadUrl(
            `${apiUrl()}/executions/${props.execution.id}`,
            `execution-${props.execution.id}.json`,
        )
    }
</script>
