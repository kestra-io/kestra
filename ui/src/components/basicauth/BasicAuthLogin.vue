<template>
    <div class="basic-auth-login">
        <div class="d-flex justify-content-center">
            <Logo class="logo" />
        </div>

        <el-form @submit.prevent="handleSubmit" :model="credentials" ref="form">
            <input type="hidden" name="from" :value="redirectPath">
            <el-form-item>
                <el-input
                    name="username"
                    size="large"
                    id="input-username"
                    v-model="credentials.username"
                    :placeholder="t('email')"
                    :class="{'input-error': showEmailError}"
                    @blur="handleEmailBlur"
                    @input="handleEmailInput"
                >
                    <template #prepend>
                        <Account />
                    </template>
                </el-input>
                <transition name="slide-fade">
                    <div v-if="showEmailError" class="error-text">
                        {{ emailErrorMessage }}
                    </div>
                </transition>
            </el-form-item>
            <el-form-item>
                <el-input
                    v-model="credentials.password"
                    size="large"
                    name="password"
                    id="input-password"
                    :placeholder="t('password')"
                    type="password"
                    :class="{'input-error': showPasswordError}"
                    showPassword
                    @blur="handlePasswordBlur"
                    @input="handlePasswordInput"
                >
                    <template #prepend>
                        <Lock />
                    </template>
                </el-input>
                <transition name="slide-fade">
                    <div v-if="showPasswordError" class="error-text">
                        {{ passwordErrorMessage }}
                    </div>
                </transition>
            </el-form-item>

            <transition name="slide-fade">
                <div v-if="authError" class="auth-error-text">
                    {{ authError }}
                </div>
            </transition>

            <el-form-item class="submit-section">
                <el-button
                    type="primary"
                    class="w-100"
                    size="large"
                    nativeType="submit"
                    :disabled="isLoginDisabled"
                    :loading="isLoading"
                >
                    {{ t("setup.login") }}
                </el-button>
            </el-form-item>
            <el-form-item>
                <el-button
                    type="default"
                    class="w-100"
                    size="large"
                    @click="openTroubleshootingGuide"
                >
                    {{ t("setup.troubleshooting") }}
                </el-button>
            </el-form-item>
        </el-form>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, nextTick} from "vue"
    import {useRouter, useRoute} from "vue-router"
    import {useI18n} from "vue-i18n"
    import {ElMessage} from "element-plus"
    import type {FormInstance} from "element-plus"
    import axios from "axios"

    import Account from "vue-material-design-icons/Account.vue"
    import Lock from "vue-material-design-icons/Lock.vue"
    import Logo from "../home/Logo.vue"

    import {useCoreStore} from "../../stores/core"
    import {useMiscStore} from "override/stores/misc"
    import {useSurveySkip} from "../../composables/useSurveyData"
    import {apiUrlWithoutTenants, apiUrl} from "override/utils/route"
    import * as BasicAuth from "../../utils/basicAuth";

    interface Credentials {
        username: string
        password: string
    }

    const router = useRouter()
    const route = useRoute()
    const {t} = useI18n()
    const coreStore = useCoreStore()
    const miscStore = useMiscStore()
    const {shouldShowHelloDialog} = useSurveySkip()

    const form = ref<FormInstance>()
    const isLoading = ref(false)
    const authError = ref<string | null>(null)
    const credentials = ref<Credentials>({username: "", password: ""})

    const emailErrorMessage = ref<string>("")
    const passwordErrorMessage = ref<string>("")
    const emailTouched = ref(false)
    const passwordTouched = ref(false)

    const showEmailError = computed(() => emailTouched.value && emailErrorMessage.value !== "")
    const showPasswordError = computed(() => passwordTouched.value && passwordErrorMessage.value !== "")

    const EMAIL_REGEX = /^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$/

    const redirectPath = computed(() => (route.query.from as string) ?? "/welcome")

    const isLoginDisabled = computed(() => {
        const hasUsername = !!credentials.value.username?.trim()
        const hasPassword = !!credentials.value.password?.trim()
        const hasErrors = showEmailError.value || showPasswordError.value
        return !hasUsername || !hasPassword || hasErrors || isLoading.value
    })

    const validateEmail = () => {
        const email = credentials.value.username.trim()
                
        if (!email) {
            emailErrorMessage.value = "Email is required"
            return false
        }
        
        if (!EMAIL_REGEX.test(email)) {
            emailErrorMessage.value = "Please enter a valid email address"
            return false
        }
        
        emailErrorMessage.value = ""
        return true
    }

    const validatePassword = () => {
        const password = credentials.value.password
        
        if (!password) {
            passwordErrorMessage.value = "Password is required"
            return false
        }
        
        passwordErrorMessage.value = ""
        return true
    }

    const handleEmailBlur = async () => {
        emailTouched.value = true
        validateEmail()
        await nextTick()
    }

    const handleEmailInput = () => {
        if (emailTouched.value) {
            validateEmail()
        }
        if (authError.value) {
            authError.value = null
        }
    }

    const handlePasswordBlur = async () => {
        passwordTouched.value = true
        validatePassword()
        await nextTick() 
    }

    const handlePasswordInput = () => {
        if (passwordTouched.value) {
            validatePassword()
        }
        if (authError.value) {
            authError.value = null
        }
    }

    const validateCredentials = async (auth: string) => {
        try {
            document.cookie = `BASIC_AUTH=${auth};path=/;samesite=strict`;
            await axios.get(`${apiUrl()}/usages/all`, {
                timeout: 10000,
                withCredentials: true
            })
        } catch(e: any) {
            BasicAuth.logout();
            
            if (e.response?.status === 401) {
                throw new Error("INVALID_CREDENTIALS")
            } else if (e.code === "ECONNABORTED" || e.message.includes("timeout")) {
                throw new Error("TIMEOUT")
            } else if (handleNetworkError(e)) {
                throw new Error("NETWORK_ERROR")
            }
            
            throw e;
        }
    }

    const checkServerInitialization = async () => {
        try {
            const response = await axios.get(`${apiUrlWithoutTenants()}/configs`, {
                timeout: 10000,
                withCredentials: true
            })
            return response.data?.isBasicAuthInitialized
        } catch (error: any) {
            if (error.response?.status === 401 || handleNetworkError(error)) {
                return false
            }
            throw error
        }
    }

    const handleNetworkError = (error: any) => {
        return error.code === "ERR_NETWORK" ||
            error.code === "ECONNREFUSED" ||
            (!error.response && error.message.includes("Network Error"))
    }

    const showAuthError = (message: string) => {
        authError.value = message
    }

    const loadAuthConfigErrors = async () => {
        try {
            const errors = await miscStore.loadBasicAuthValidationErrors()
            if (errors && errors.length > 0) {
                errors.forEach((error: string) => {
                    ElMessage.error({
                        message: `${error}. ${t("setup.validation.config_message")}`,
                        duration: 5000,
                        showClose: true
                    })
                })
                return true
            }
            return false
        } catch (error: any) {
            if (error.response?.status === 401) {
                return false
            }
            return false
        }
    }

    const handleSubmit = async (event: Event) => {
        coreStore.error = undefined;
        authError.value = null
        event.preventDefault()
        
        emailTouched.value = true
        passwordTouched.value = true
        
        const isEmailValid = validateEmail()
        const isPasswordValid = validatePassword()
        
        if (!isEmailValid || !isPasswordValid) {
            return
        }
        
        if (isLoading.value) return

        isLoading.value = true

        try {
            const {username, password} = credentials.value
            const trimmedUsername = username.trim()
            const auth = btoa(`${trimmedUsername}:${password}`)

            try {
                await validateCredentials(auth)
            } catch (err: any) {
                if (err.message === "INVALID_CREDENTIALS") {
                    showAuthError("Incorrect username or password.")
                    await loadAuthConfigErrors()
                } else if (err.message === "TIMEOUT") {
                    showAuthError("Request timed out. Please check your connection and try again.")
                } else if (err.message === "NETWORK_ERROR") {
                    showAuthError("Unable to reach the server. Please check if the server is running.")
                    router.push({name: "setup"})
                } else {
                    showAuthError("Login failed. Please try again.")
                }
                return
            }

            const isInitialized = await checkServerInitialization()
            if (!isInitialized) {
                router.push({name: "setup"})
                return
            }

            BasicAuth.signIn(trimmedUsername, password)
            localStorage.removeItem("basicAuthSetupInProgress")
            sessionStorage.setItem("sessionActive", "true")
            credentials.value = {username: "", password: ""}

            if (shouldShowHelloDialog()) {
                localStorage.setItem("showSurveyDialogAfterLogin", "true")
            }

            router.push(redirectPath.value)
        } catch (error: any) {
            if (handleNetworkError(error)) {
                showAuthError("Network error. The server may not be reachable.")
                router.push({name: "setup"})
                return
            }

            showAuthError("An unexpected error occurred. Please try again.")
        } finally {
            isLoading.value = false
        }
    }

    const openTroubleshootingGuide = () => {
        window.open("https://kestra.io/docs/administrator-guide/basic-auth-troubleshooting", "_blank")
    }
</script>

<style scoped lang="scss">
.basic-auth-login {
    flex-shrink: 1;
    width: 400px;

    .logo {
        width: 250px;
        margin-bottom: 40px;
    }

    .el-button.el-button--default {
        background: var(--bs-gray-200);

        html.dark & {
            background: var(--input-bg);

            &.el-button {
                border: 0;
            }
        }
    }

    .el-form-item {
        margin-bottom: 20px;

        .el-input {
            height: 54px;
        }

        .el-input-group__prepend {
            .material-design-icon {
                .material-design-icon__svg {
                    width: 1.5em;
                    height: 1.5em;
                    bottom: -0.250em;
                }
            }
        }
    }

    .submit-section {
        margin-top: 28px;
    }

    .el-input.input-error {
        :deep(.el-input__wrapper) {
            box-shadow: 0 0 0 1px #e74c3c inset !important;
            border-color: #e74c3c !important;
            
            &:hover, &:focus {
                box-shadow: 0 0 0 1px #e74c3c inset !important;
            }
        }
    }

    .error-text {
        color: #e74c3c;
        font-size: 13px;
        font-weight: 500;
        line-height: 1.3;
        margin-top: 8px;
        padding-left: 2px;
        display: flex;
        align-items: center;
        
        html.dark & {
            color: #ff6b6b;
        }

        &::before {
            content: "⚠";
            margin-right: 6px;
            font-size: 14px;
        }
    }

    .auth-error-text {
        color: #c0392b;
        font-size: 14px;
        font-weight: 500;
        line-height: 1.4;
        margin-bottom: 20px;
        padding: 0 2px;
        text-align: center;
        
        html.dark & {
            color: #ff6b6b;
        }

        &::before {
            content: "✕";
            display: inline-block;
            margin-right: 8px;
            font-weight: 700;
            font-size: 15px;
        }
    }

    .slide-fade-enter-active {
        transition: all 0.25s ease-out;
    }
    
    .slide-fade-leave-active {
        transition: all 0.18s ease-in;
    }

    .slide-fade-enter-from {
        opacity: 0;
        transform: translateY(-8px);
    }

    .slide-fade-leave-to {
        opacity: 0;
        transform: translateY(-4px);
    }
}
</style>
