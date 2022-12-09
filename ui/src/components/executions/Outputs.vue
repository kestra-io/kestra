<template>
    <el-dropdown-item
        :icon="LocationExit"
        :disabled="!outputs || outputs.length === 0"
        @click="isOpen = !isOpen"
    >
        {{ $t('outputs') }}
    </el-dropdown-item>

    <el-drawer
        v-if="isOpen"
        v-model="isOpen"
        :title="$t('outputs')"
        destroy-on-close
        :append-to-body="true"
        size=""
        direction="ltr"
    >
        <vars
            :execution="execution"
            class="table-unrounded mt-1"
            :data="outputs"
        />
    </el-drawer>
</template>

<script setup>
    import LocationExit from "vue-material-design-icons/LocationExit.vue";
</script>

<script>
    import Vars from "../executions/Vars";
    import Timer from "vue-material-design-icons/Timer";
    import Counter from "vue-material-design-icons/Numeric";

    export default {
        components: {
            Timer,
            Counter,
            Vars,
        },
        props: {
            outputs: {
                type: Object,
                required: false,
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
