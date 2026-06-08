<template>
    <div class="oss-login">
        <div class="oss-login__brand">
            <img src="../../assets/icon.svg" alt="Kestra" class="oss-login__icon" />
            <h1 class="oss-login__title">{{ $t("setup.login_title") }}</h1>
        </div>

        <form class="oss-login__form" @submit.prevent="handleSubmit">
            <input type="hidden" name="from" :value="redirectPath">

            <div class="login-field" :class="{'login-field--error': errors.username}">
                <AccountOutline class="login-field__icon" />
                <input
                    class="login-field__input"
                    name="username"
                    id="input-username"
                    v-model="credentials.username"
                    :placeholder="$t('email')"
                    type="email"
                    required
                    autocomplete="username"
                    @blur="validateEmailField"
                />
            </div>
            <p v-if="errors.username" class="login-field-error">{{ errors.username }}</p>

            <div class="login-field" :class="{'login-field--error': errors.password}">
                <LockOutline class="login-field__icon" />
                <input
                    class="login-field__input"
                    name="password"
                    id="input-password"
                    v-model="credentials.password"
                    :type="showPasswordText ? 'text' : 'password'"
                    :placeholder="$t('password')"
                    required
                    autocomplete="current-password"
                    @blur="validatePasswordField"
                />
                <button class="login-field__eye" type="button" @click="showPasswordText = !showPasswordText" tabindex="-1">
                    <EyeOutline v-if="!showPasswordText" />
                    <EyeOffOutline v-else />
                </button>
            </div>
            <p v-if="errors.password" class="login-field-error">{{ errors.password }}</p>

            <button class="login-btn" type="submit" :disabled="isLoading">
                <span v-if="!isLoading">{{ $t("setup.login") }}</span>
                <span v-else class="login-btn__spinner" />
            </button>
        </form>

        <button class="login-trouble" type="button" @click="openTroubleshootingGuide">
            {{ $t("setup.troubleshooting") }}
        </button>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed} from "vue"
    import {useRouter, useRoute} from "vue-router"
    import {useI18n} from "vue-i18n"
    import {KsMessage} from "@kestra-io/design-system"
    import {useClient} from "@kestra-io/kestra-sdk"
    import MailChecker from "mailchecker"

    import AccountOutline from "vue-material-design-icons/AccountOutline.vue"
    import LockOutline from "vue-material-design-icons/LockOutline.vue"
    import EyeOutline from "vue-material-design-icons/EyeOutline.vue"
    import EyeOffOutline from "vue-material-design-icons/EyeOffOutline.vue"

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

    const isLoading = ref(false)
    const showPasswordText = ref(false)
    const credentials = ref<Credentials>({username: "", password: ""})
    const errors = ref<{username?: string; password?: string}>({})

    const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    const PASSWORD_REGEX = /^(?=.*[A-Z])(?=.*\d)\S{8,}$/

    const redirectPath = computed(() => route.query.from as string | undefined)

    function validateEmailField() {
        const val = credentials.value.username
        if (!val?.trim()) {
            errors.value.username = t("setup.validation.email_required")
        } else if (!EMAIL_REGEX.test(val)) {
            errors.value.username = t("setup.validation.email_invalid")
        } else if (!MailChecker.isValid(val)) {
            errors.value.username = t("setup.validation.email_temporary_not_allowed")
        } else {
            errors.value.username = undefined
        }
    }

    function validatePasswordField() {
        const val = credentials.value.password
        if (!val || !PASSWORD_REGEX.test(val)) {
            errors.value.password = t("setup.validation.password_invalid")
        } else {
            errors.value.password = undefined
        }
    }

    function isFormValid() {
        validateEmailField()
        validatePasswordField()
        return !errors.value.username && !errors.value.password
    }

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
            if (isLoading.value) return
            if (!isFormValid()) return

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
                KsMessage.error("Login failed")
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
    .oss-login {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--ks-spacing-6);
        width: 320px;

        &__brand {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: var(--ks-spacing-4);
        }

        &__icon {
            width: 3.9286rem;
            height: 3.9286rem;
        }

        &__title {
            margin: 0;
            font-size: var(--ks-font-size-xl);
            font-weight: 600;
            color: var(--ks-text-primary);
            text-align: center;
        }

        &__form {
            display: flex;
            flex-direction: column;
            gap: var(--ks-spacing-4);
            width: 100%;
        }
    }

    .login-field {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        height: 30px;
        padding: var(--ks-spacing-2);
        background: var(--ks-bg-input);
        border: 1px solid var(--ks-border-strong);
        border-radius: 0.5rem;
        box-shadow: 0 1px 2px var(--ks-shadow-element);
        width: 100%;
        box-sizing: border-box;

        &--error {
            border-color: var(--ks-border-error);
        }

        &__icon {
            flex-shrink: 0;
            color: var(--ks-text-secondary);

            :deep(svg) {
                width: 14px;
                height: 14px;
            }
        }

        &__input {
            flex: 1;
            min-width: 0;
            background: transparent;
            border: none;
            outline: none;
            font-size: var(--ks-font-size-xs);
            color: var(--ks-text-primary);
            font-family: inherit;

            &::placeholder {
                color: var(--ks-text-secondary);
            }
        }

        &__eye {
            flex-shrink: 0;
            display: flex;
            align-items: center;
            background: none;
            border: none;
            padding: 0;
            cursor: pointer;
            color: var(--ks-text-secondary);

            :deep(svg) {
                width: 14px;
                height: 14px;
            }

            &:hover {
                color: var(--ks-text-primary);
            }
        }
    }

    .login-field-error {
        margin-top: calc(-1 * var(--ks-spacing-2));
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-error);
    }

    .login-btn {
        width: 100%;
        height: 32px;
        background: var(--ks-btn-primary-bg-default);
        border: none;
        border-radius: 0.5rem;
        color: white;
        font-size: var(--ks-font-size-sm);
        font-weight: 600;
        cursor: pointer;
        box-shadow: 0 1px 2px var(--ks-shadow-element);
        font-family: inherit;
        display: flex;
        align-items: center;
        justify-content: center;

        &:hover:not(:disabled) {
            opacity: 0.9;
        }

        &:disabled {
            opacity: 0.6;
            cursor: not-allowed;
        }

        &__spinner {
            width: 14px;
            height: 14px;
            border: 2px solid rgba(255, 255, 255, 0.3);
            border-top-color: white;
            border-radius: 50%;
            animation: spin 0.6s linear infinite;
        }
    }

    .login-trouble {
        background: none;
        border: none;
        padding: 0;
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-secondary);
        cursor: pointer;
        text-align: center;
        font-family: inherit;

        &:hover {
            color: var(--ks-text-primary);
        }
    }

    @keyframes spin {
        to { transform: rotate(360deg); }
    }
</style>
