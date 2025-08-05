<template>
    <el-tooltip
        data-component="FILENAME_PLACEHOLDER"
        :key="uid('tooltip')"
        v-if="date"
        :content="inverted ? from : full"
        :persistent="false"
        transition=""
        :hide-after="0"
        effect="light"
    >
        <span :class="className">{{ inverted ? full : from }}</span>
    </el-tooltip>
</template>
<script lang="ts" setup>
    import {computed, getCurrentInstance} from "vue";
    import Utils from "../../utils/utils";

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
        }
    })

    function uid(key: string) {
        return key + "-" + Utils.uid();
    }
    const {$moment, $filters} = getCurrentInstance()?.appContext.config.globalProperties || {} as any;

    const from = computed(() => {
        return $moment(props.date).fromNow();
    })
    const full = computed(() => {
        return $filters.date(props.date, props.format);
    })
</script>
