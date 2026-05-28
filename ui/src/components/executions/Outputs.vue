<template>
    <KsDropdownItem :disabled :icon="LocationExit" @click="isOpen = !isOpen">
        {{ $t("outputs") }}
    </KsDropdownItem>

    <KsDrawer v-if="isOpen" v-model="isOpen" :title="$t('outputs')">
        <Vars
            :execution="props.execution"
            class="table-unrounded mt-1"
            :data="props.outputs"
        />
    </KsDrawer>
</template>

<script setup lang="ts">
    import {computed, ref, type PropType} from "vue"

    import Vars from "../executions/Vars.vue"

    import LocationExit from "vue-material-design-icons/LocationExit.vue"

    const props = defineProps({
        outputs: {
            type: Object as PropType<object>,
            default: () => ({}),
        },
        execution: {
            type: Object as PropType<object>,
            required: true,
        },
    })

    const isOpen = ref(false)

    const disabled = computed(() => !props.outputs || Object.keys(props.outputs).length === 0)
</script>
