<template>
    <span>
        {{ humanReadableCron }}
    </span>
</template>

<script lang="ts" setup>
    import Utils from "../../utils/utils";
    import cronstrue from "cronstrue";
    import "cronstrue/locales/fr";
    import {computed} from "vue";

    const props =defineProps<{
        cronExpression:string;
    }>()

    const humanReadableCron = computed(() => {
        return cronstrue.toString(props.cronExpression, {locale: Utils.getLang()});
    });

</script>