<template>
    <div class="oss-login">
        <img src="../../assets/monogram.svg" alt="Kestra" class="oss-login__icon" />
        <h1 class="oss-login__title">{{ $t("setup.login_title") }}</h1>

        <KsForm ref="form" :model="credentials" :rules="rules" class="oss-login__form" @submit.prevent="handleSubmit">
            <input type="hidden" name="from" :value="redirectPath">

            <KsFormItem prop="username">
                <KsInput
                    id="input-username"
                    v-model="credentials.username"
                    name="username"
                    type="email"
                    :placeholder="$t('email')"
                    autocomplete="username"
                >
                    <template #prefix><KsIcon><AccountOutline /></KsIcon></template>
                </KsInput>
            </KsFormItem>

            <KsFormItem prop="password">
                <KsInput
                    id="input-password"
                    v-model="credentials.password"
                    name="password"
                    showPassword
                    :placeholder="$t('password')"
                    autocomplete="current-password"
                >
                    <template #prefix><KsIcon><LockOutline /></KsIcon></template>
                </KsInput>
            </KsFormItem>

            <KsButton
                type="primary"
                nativeType="submit"
                class="oss-login__submit"
                :loading="isLoading"
                :disabled="isLoading"
            >
                {{ $t("setup.login") }}
            </KsButton>

            <KsButton text type="primary" @click="openTroubleshootingGuide">
                {{ $t("setup.troubleshooting") }}
            </KsButton>
        </KsForm>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed} from "vue"
    import {useRouter, useRoute} from "vue-router"
    import {useI18n} from "vue-i18n"
    import {KsMessage, KsIcon} from "@kestra-io/design-system"
    import type {FormInstance} from "@kestra-io/design-system"
    import {useClient} from "@kestra-io/kestra-sdk"

    import AccountOutline from "vue-material-design-icons/AccountOutline.vue"
    import LockOutline from "vue-material-design-icons/LockOutline.vue"

    import {useCoreStore} from "../../stores/core"
    import {useApiStore} from "../../stores/api"
    import {useMiscStore} from "override/stores/misc"
    import {useSurveySkip} from "../../composables/useSurveyData"
    import {apiUrlWithoutTenants, apiUrl} from "override/utils/route"
    import * as BasicAuth from "../../utils/basicAuth"
    import {shouldShowWelcome} from "../../utils/welcomeGuard"
    import {identifyPosthogUser} from "../../utils/posthog"

    interface Credentials {
        username: string
        password: string
    }

    const router = useRouter()
    const route = useRoute()
    const {t} = useI18n()
    const coreStore = useCoreStore()
    const apiStore = useApiStore()
    const miscStore = useMiscStore()
    const {shouldShowHelloDialog} = useSurveySkip()

    const form = ref<FormInstance>()
    const isLoading = ref(false)
    const credentials = ref<Credentials>({username: "", password: ""})

    const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

    const redirectPath = computed(() => route.query.from as string | undefined)

    const validateEmail = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
        if (!value?.trim()) {
            callback(new Error(t("setup.validation.email_required")))
        } else if (!EMAIL_REGEX.test(value)) {
            callback(new Error(t("setup.validation.email_invalid")))
        } else {
            callback()
        }
    }

    const validatePassword = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
        if (!value) {
            callback(new Error(t("setup.validation.password_required")))
        } else {
            callback()
        }
    }

    const rules = computed(() => ({
        username: [{required: true, validator: validateEmail, trigger: "blur"}],
        password: [{required: true, validator: validatePassword, trigger: "blur"}],
    }))

    const axios = useClient()

    const validateCredentials = async (auth: string) => {
        try {
            document.cookie = `BASIC_AUTH=${auth};path=/;samesite=strict`
            await axios.get(`${apiUrl()}/usages/all`, {timeout: 10000, withCredentials: true})
        } catch(e) {
            BasicAuth.logout()
            throw e
        }
    }

    const checkServerInitialization = async () => {
        const response = await axios.get(`${apiUrlWithoutTenants()}/configs`, {timeout: 10000, withCredentials: true})
        return response.data?.isBasicAuthInitialized
    }

    const handleNetworkError = (error: any) => {
        return error.code === "ERR_NETWORK" ||
            error.code === "ECONNREFUSED" ||
            (!error.response && error.message?.includes("Network Error"))
    }

    const loadAuthConfigErrors = async () => {
        try {
            const errs = await miscStore.loadBasicAuthValidationErrors()
            if (errs?.length) {
                errs.forEach((err: string) => {
                    KsMessage.error({message: `${err}. ${t("setup.validation.config_message")}`, duration: 5000, showClose: false})
                })
            } else {
                KsMessage.error({message: t("setup.validation.incorrect_creds")})
            }
        } catch {
            KsMessage.error({message: t("setup.validation.incorrect_creds")})
        }
    }

    const handleSubmit = async () => {
        try {
            coreStore.error = undefined
            if (!form.value || isLoading.value) return
            if (!(await form.value.validate().catch(() => false))) return

            isLoading.value = true

            const {username, password} = credentials.value
            const trimmedUsername = username.trim()
            const auth = btoa(`${trimmedUsername}:${password}`)

            await validateCredentials(auth)

            const isInitialized = await checkServerInitialization()
            if (!isInitialized) { router.push({name: "setup"}); return }

            BasicAuth.signIn(trimmedUsername, password)
            localStorage.removeItem("basicAuthSetupInProgress")
            sessionStorage.setItem("sessionActive", "true")

            const configs = await miscStore.loadConfigs()
            await identifyPosthogUser(configs, {email: trimmedUsername})
            credentials.value = {username: "", password: ""}

            if (shouldShowHelloDialog()) localStorage.setItem("showSurveyDialogAfterLogin", "true")

            if (await shouldShowWelcome()) {
                router.push({name: "welcome"})
            } else if (redirectPath.value) {
                router.push(redirectPath.value)
            } else {
                router.push({name: "home", params: {tenant: route.params.tenant}})
            }
        } catch (error: any) {
            if (handleNetworkError(error)) { router.push({name: "setup"}); return }
            if (error?.response?.status === 401) {
                await loadAuthConfigErrors()
            } else if (error?.response?.status === 404) {
                router.push({name: "setup"})
            } else {
                KsMessage.error(t("setup.validation.incorrect_creds"))
            }
        } finally {
            isLoading.value = false
        }
    }

    const openTroubleshootingGuide = () => {
        apiStore.posthogEvents({type: "ossauth", action: "forgot_password_click"})
        window.open("https://kestra.io/docs/administrator-guide/basic-auth-troubleshooting?utm_source=app&utm_medium=referral&utm_campaign=login&utm_content=lost-password", "_blank")
    }
</script>

<style scoped lang="scss">
    :global(body .fullscreen-layout:has(.oss-login)) {
        background: var(--ks-bg-base) url("../onboarding/assets/grid.svg") center calc(50% + 165px) / auto no-repeat;
    }

    .oss-login {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--ks-spacing-6);
        width: 320px;
    }

    .oss-login__icon {
        width: 3.5rem;
        height: 3.5rem;
    }

    .oss-login__title {
        margin: 0;
        font-size: var(--ks-font-size-xl);
        font-weight: 600;
        color: var(--ks-text-primary);
        text-align: center;
    }

    .oss-login__form {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-4);
        width: 100%;
    }

    .oss-login__submit {
        width: 100%;
    }
</style>
