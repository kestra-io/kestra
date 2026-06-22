<template>
    <ul class="ks-password-requirements">
        <li
            v-for="rule in computedRules"
            :key="rule.key"
            class="ks-password-requirements__item"
        >
            <KsCheckItem :met="rule.met">{{ rule.label ?? t(`password_requirements.${rule.key}`) }}</KsCheckItem>
        </li>
    </ul>
</template>

<script setup lang="ts">
    import {computed, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import KsCheckItem from "../Data/KsCheckItem.vue"

    export type PasswordRule = {
        key: string
        test: (password: string) => boolean
        label?: string
    }

    const DEFAULT_RULES: PasswordRule[] = [
        {key: "length", test: (p) => p.length >= 8},
        {key: "uppercase", test: (p) => /[A-Z]/.test(p)},
        {key: "lowercase", test: (p) => /[a-z]/.test(p)},
        {key: "number", test: (p) => /[0-9]/.test(p)},
    ]

    const props = withDefaults(defineProps<{
        password?: string
        rules?: PasswordRule[]
    }>(), {
        password: "",
    })

    const emit = defineEmits<{
        (e: "update:valid", valid: boolean): void
    }>()

    const {t} = useI18n({useScope: "global"})

    const computedRules = computed(() =>
        (props.rules ?? DEFAULT_RULES).map((rule) => ({...rule, met: rule.test(props.password)})),
    )

    const valid = computed(() => computedRules.value.every((rule) => rule.met))

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
