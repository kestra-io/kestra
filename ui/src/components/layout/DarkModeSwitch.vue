<script setup lang="ts">
    import {useDark, useToggle} from "@vueuse/core"
    import WeatherSunny from "vue-material-design-icons/WeatherSunny.vue"
    import WeatherNight from "vue-material-design-icons/WeatherNight.vue"

    const isDark = useDark()
    const toggleDark = useToggle(isDark)
</script>

<template>
    <el-button @click="() => toggleDark()" class="dark-mode-switch">
        <WeatherNight v-if="isDark" />
        <WeatherSunny v-else />
        <span style="margin-left:.5rem">{{ isDark ? 'Dark' : 'Light' }}</span>
    </el-button>
</template>

<style lang="scss" scoped>
    .dark-mode-switch{
        position: absolute;
        top: 1rem;
        right: 1rem;
    }
</style>
