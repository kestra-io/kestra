<template>
    <div>
        <a class="el-button el-button--large el-button--primary" target="_blank" :href="getADemoUrl.href">
            {{ $t("demos.get_a_demo_button") }}
        </a>
        <el-button size="large" @click="store.commit('misc/setContextInfoBarOpenTab', 'docs')">
            Learn More
            <el-icon class="el-icon--right">
                <ArrowRightIcon />
            </el-icon>
        </el-button>
    </div>
</template>

<script setup lang="ts">
    import ArrowRightIcon from "vue-material-design-icons/ArrowRight.vue";
    import {useRoute} from "vue-router";
    import {useStore} from "vuex";
    import {computed} from "vue";

    const store = useStore();
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