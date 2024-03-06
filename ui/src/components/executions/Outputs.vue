<template>
    <el-dropdown-item
        :icon="LocationExit"
        :disabled="!outputs || outputs.length === 0"
        @click="isOpen = !isOpen"
    >
        {{ $t('outputs') }}
    </el-dropdown-item>

    <drawer
        v-if="isOpen"
        v-model="isOpen"
        :title="$t('outputs')"
    >
        <vars
            :execution="execution"
            class="table-unrounded mt-1"
            :data="outputs"
        />
    </drawer>
</template>

<script setup>
    import LocationExit from "vue-material-design-icons/LocationExit.vue";
</script>

<script>
    import Vars from "../executions/Vars.vue";
    import Drawer from "../Drawer.vue";

    export default {
        components: {
            Vars,
            Drawer,
        },
        props: {
            outputs: {
                type: Object,
                required: false,
                default: () => {}
            },
            execution: {
                type: Object,
                required: true
            }
        },
        data() {
            return {
                isOpen: false,
            };
        },
    };
</script>
