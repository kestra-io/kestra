<template>
    <div class="oss-login">
        <img src="../../assets/monogram.svg" alt="Kestra" class="oss-login__icon" />
        <h1 class="oss-login__title">{{ $t("setup.login_title") }}</h1>

        <div v-if="hint" class="oss-login__hint" data-test="login-after-sign-in">
            <span class="oss-login__hint-label">{{ $t("login_redirect_hint.after_sign_in") }}</span>
            <strong class="oss-login__hint-target">{{ hint.namespace }} / {{ hint.flowId }}</strong>
            <span v-if="hintDetail" class="oss-login__hint-detail">{{ hintDetail }}</span>
        </div>

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
    import type {KestraHttpError} from "../../utils/kestraHttp"

    import AccountOutline from "vue-material-design-icons/AccountOutline.vue"
    import LockOutline from "vue-material-design-icons/LockOutline.vue"

    import {useCoreStore} from "../../stores/core"
    import {useApiStore} from "../../stores/api"
    import {useMiscStore} from "override/stores/misc"
    import {useSurveySkip} from "../../composables/useSurveyData"
    import {apiUrlWithoutTenants} from "override/utils/route"
    import * as BasicAuth from "../../utils/basicAuth"
    import {shouldShowWelcome} from "../../utils/welcomeGuard"
    import {identifyPosthogUser} from "../../utils/posthog"
    import {storageKeys} from "../../utils/constants"
    import {loginRedirectHint} from "../../utils/loginRedirectHint"

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

    const SHORT_ID_LENGTH = 8
    const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

    const withoutRouterBase = (path: string) => {
        const base = router.options.history.base
        if (!base || base === "/") return path
        if (path === base) return "/"
        return path.startsWith(`${base}/`) ? path.slice(base.length) : path
    }

    const redirectPath = computed(() => {
        const raw = route.query.from as string | undefined
        return raw && raw.startsWith("/") && !raw.startsWith("//") ? withoutRouterBase(raw) : undefined
    })

    const hint = computed(() => loginRedirectHint(redirectPath.value))

    const hintDetail = computed(() => {
        const current = hint.value
        if (!current) return ""
        const id = current.executionId?.slice(0, SHORT_ID_LENGTH)
        const tab = current.tabLabelKey ? t(current.tabLabelKey) : undefined
        if (id && tab) return t("login_redirect_hint.execution_tab", {id, tab})
        if (id) return t("login_redirect_hint.execution", {id})
        return tab ? t("login_redirect_hint.tab", {tab}) : ""
    })

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


    const checkServerInitialization = async () => {
        const response = await axios.get(`${apiUrlWithoutTenants()}/configs/login`, {timeout: 10000})
        return response.data?.isBasicAuthInitialized
    }

    /**
     * `useClient()` is a fetch-based facade, not axios. A network-level failure rejects with the raw
     * fetch `TypeError`, before the interceptors in `kestraHttp` can attach a `response`; the axios-only
     * `ERR_NETWORK` and `ECONNREFUSED` codes this used to also test for are never set by the facade.
     */
    const handleNetworkError = (error: unknown) => error instanceof TypeError

    /** Every other rejection is a `KestraHttpError`: the parsed error body those same interceptors decorate. */
    const responseStatus = (error: unknown) => (error as KestraHttpError | null)?.response?.status

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
            
            const isInitialized = await checkServerInitialization()
            if (!isInitialized) { router.push({name: "setup"}); return }

            const {username: trimmedUsername} = await BasicAuth.signIn(credentials.value)
            localStorage.removeItem(storageKeys.BASIC_AUTH_SETUP_IN_PROGRESS)
            sessionStorage.setItem(storageKeys.SESSION_ACTIVE, "true")

            const configs = await miscStore.loadConfigs()
            await identifyPosthogUser(configs, {email: trimmedUsername})
            credentials.value = {username: "", password: ""}

            if (shouldShowHelloDialog()) localStorage.setItem(storageKeys.SHOW_SURVEY_DIALOG_AFTER_LOGIN, "true")

            if (redirectPath.value && hint.value) {
                router.push(redirectPath.value)
            } else if (await shouldShowWelcome()) {
                router.push({name: "ai"})
            } else if (redirectPath.value) {
                router.push(redirectPath.value)
            } else {
                router.push({name: "home", params: {tenant: route.params.tenant}})
            }
        } catch (error: unknown) {
            if (handleNetworkError(error)) {
                router.push({name: "setup"})
                return
            }

            const status = responseStatus(error)

            if (status === 401) {
                await loadAuthConfigErrors()
            } else if (status === 404) {
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

<style scoped>
    :global(body .fullscreen-layout:has(.oss-login)) {
        background: var(--ks-bg-base) url("../onboarding/assets/grid.svg") center calc(50% + 165px) / auto no-repeat;
    }

    .oss-login {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--ks-spacing-6);
        width: min(20rem, 100%);
    }

    .oss-login__hint {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
        width: 100%;
        padding: var(--ks-spacing-3);
        background: var(--ks-bg-info);
        border: 1px solid var(--ks-border-info);
        border-radius: var(--ks-radius-base);
        overflow-wrap: anywhere;
    }

    .oss-login__hint-label {
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
        letter-spacing: 0.04em;
        text-transform: uppercase;
    }

    .oss-login__hint-target {
        color: var(--ks-text-primary);
    }

    .oss-login__hint-detail {
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
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
        gap: var(--ks-spacing-1);
        width: 100%;
    }

    .oss-login__submit {
        width: 100%;
    }
</style>
