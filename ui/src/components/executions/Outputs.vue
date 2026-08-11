<template>
    <KsDropdownItem :disabled :icon="LocationExit" @click="isOpen = !isOpen">
        {{ $t("outputs") }}
    </KsDropdownItem>

    <KsDrawer v-if="isOpen" v-model="isOpen" :title="$t('outputs')">
        <div v-ks-loading="isLoading">
            <Vars
                :execution="props.execution"
                class="table-unrounded mt-1"
                :data="outputs"
            />
        </div>
    </KsDrawer>
</template>

<script setup lang="ts">
    import {computed, ref, watch, type PropType} from "vue"
    import {vKsLoading} from "@kestra-io/design-system"

    import Vars from "../executions/Vars.vue"

    import {useHasTaskRunOutputs, loadTaskRunOutputs} from "../../composables/useTaskRunOutputs"

    import LocationExit from "vue-material-design-icons/LocationExit.vue"

    const props = defineProps({
        taskRun: {
            type: Object as PropType<{id: string; state: {current: string}}>,
            required: true,
        },
        executionId: {
            type: String,
            required: true,
        },
        execution: {
            type: Object as PropType<object>,
            required: true,
        },
    })

    const isOpen = ref(false)
    const outputs = ref<Record<string, unknown>>({})
    const isLoading = ref(false)

    const hasOutputs = useHasTaskRunOutputs(
        computed(() => props.executionId),
        computed(() => props.taskRun.id),
        computed(() => props.taskRun.state?.current),
    )

    const disabled = computed(() => !hasOutputs.value)

    watch(isOpen, async (open) => {
        if (!open || Object.keys(outputs.value).length > 0) {
            return
        }
        isLoading.value = true
        try {
            outputs.value = await loadTaskRunOutputs(props.executionId, props.taskRun.id)
        } finally {
            isLoading.value = false
        }
    })
</script>
