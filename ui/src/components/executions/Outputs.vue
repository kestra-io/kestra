<template>
    <el-dropdown-item
        :icon="LocationExit"
        :disabled="isDisabled"
        @click="isOpen = !isOpen"
    >
        {{ $t('outputs') }}
    </el-dropdown-item>

    <Drawer
        v-if="isOpen"
        v-model="isOpen"
        :title="$t('outputs')"
    >
        <Vars
            :execution="props.execution"
            class="table-unrounded mt-1"
            :data="props.outputs"
        />
    </Drawer>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue";
    import LocationExit from "vue-material-design-icons/LocationExit.vue";
    import Vars from "../executions/Vars.vue";
    import Drawer from "../Drawer.vue";
    import {type PropType} from "vue";

    // Define props with TypeScript 
    const props = defineProps({
        outputs: {
            type: Object as PropType<object>,
            default: () => ({})
        },
        execution: {
            type: Object as PropType<object>,
            required: true
        }
    });
    // Reactive state for the drawer
    const isOpen = ref(false);

    // Computed property to determine if the button needs to be disabled
    const isDisabled = computed(() => {
        return !props.outputs || Object.keys(props.outputs).length === 0;
    });
</script>