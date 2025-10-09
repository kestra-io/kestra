<template>
    <div>
        <a class="el-button el-button--large el-button--primary" target="_blank" :href="getADemoUrl.href">
            {{ $t("demos.get_a_demo_button") }}
        </a>
    </div>
</template>

<script setup lang="ts">
    import {useRoute} from "vue-router";
    import {computed} from "vue";

    const route = useRoute();

    const getADemoUrl = computed(() => {
        const demoUrl = new URL("https://kestra.io/demo");
        // set all utm params from the route query
        for (const [key, value] of Object.entries(route.query)) {
            if (key.startsWith("utm_")) {
                demoUrl.searchParams.set(key, value as string);
            }
        }
        return demoUrl;
    });
</script>