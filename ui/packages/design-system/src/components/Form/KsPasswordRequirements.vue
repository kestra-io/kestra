<template>
    <ul class="ks-password-requirements">
        <li
            v-for="rule in rules"
            :key="rule.key"
            class="ks-password-requirements__item"
        >
            <KsCheckItem :met="rule.met">{{ t(`password_requirements.${rule.key}`) }}</KsCheckItem>
        </li>
    </ul>
</template>

<script setup lang="ts">
    import {computed, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import KsCheckItem from "../Data/KsCheckItem.vue"

    const RULES = [
        {key: "length", test: (p: string) => p.length >= 8},
        {key: "uppercase", test: (p: string) => /[A-Z]/.test(p)},
        {key: "lowercase", test: (p: string) => /[a-z]/.test(p)},
        {key: "number", test: (p: string) => /[0-9]/.test(p)},
    ] as const

    const props = withDefaults(defineProps<{
        password?: string
    }>(), {
        password: "",
    })

    const emit = defineEmits<{
        (e: "update:valid", valid: boolean): void
    }>()

    const {t} = useI18n({useScope: "global"})

    const rules = computed(() => RULES.map((rule) => ({key: rule.key, met: rule.test(props.password)})))

    const valid = computed(() => rules.value.every((rule) => rule.met))

    watch(valid, (value) => emit("update:valid", value), {immediate: true})
</script>

<style scoped lang="scss">
    .ks-password-requirements {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
    }
</style>
