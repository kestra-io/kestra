<template>
    <div
        class="sample-wrapper"
        :style="styleComputed"
    >
        <span class="sample-block" :style="{backgroundColor: `var(--${color})`}" />
        <span class="sample-label">
            var(--{{ color }})
        </span>
    </div>
</template>

<script lang="ts" setup>
    import {computed} from "vue";


    const props = defineProps<{
        color: string
    }>()

    const styleComputed = computed(() => {
        return props.color.indexOf("background") > -1
            ? {background: `var(--${props.color})`}
            : props.color.indexOf("border") > -1
                ? {border: `1px solid var(--${props.color})`, borderRadius: "4px"}
                : {color: `var(--${props.color})`};
    })
</script>

<style scoped>

.sample-wrapper {
    padding:.5rem 1rem;
    display: flex;
    align-items: center;
    gap:.5rem;
    color: black;
}

.sample-label{
    background-color: rgba(255, 255, 255, 0.5);
    padding: 0 .5rem;
    border-radius: 4px;
}

.sample-block{
    width:24px;
    height:24px;
    display:block
}
</style>
