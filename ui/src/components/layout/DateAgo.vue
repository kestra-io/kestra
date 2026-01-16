<template>
    <el-tooltip
        v-if="showTooltip && date"
        :key="uid('tooltip')"
        :content="inverted ? from : full"
        :persistent="false"
        transition=""
        :hideAfter="0"
        effect="light"
    >
        <span :class="className">
            {{ inverted ? full : from }}
        </span>
    </el-tooltip>
    <span v-else-if="date" :class="className">
        {{ inverted ? full : from }}
    </span>
</template>
<script setup lang="ts">
    import {computed, getCurrentInstance} from "vue";
    import Utils from "../../utils/utils";
    import moment from "moment";

    const {$filters} = getCurrentInstance()?.appContext.config.globalProperties || {} as any;

    const props = defineProps({
        date: {
            type: [Date, String],
            default: undefined
        },
        inverted: {
            type: Boolean,
            default: false
        },
        format: {
            type: String,
            default: undefined
        },
        className: {
            type: String,
            default: null
        },
        showTooltip:{
            type: Boolean,
            default: true
        }
    })

    function uid(key: string) {
        return key + "-" + Utils.uid();
    }

    const from = computed(() => {
        return moment(props.date).fromNow();
    })
    const full = computed(() => {
        return $filters.date(props.date, props.format);
    })
</script>
