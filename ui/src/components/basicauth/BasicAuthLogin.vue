<template>
    <div class="basic-auth-login">
        <div class="d-flex justify-content-center">
            <Logo class="logo" />
        </div>

        <el-form @submit.prevent :model="credentials" ref="form" :rules="rules" :showMessage="false">
            <input type="hidden" name="from" :value="redirectPath">
            <el-form-item prop="username">
                <el-input
                    name="username"
                    size="large"
                    id="input-username"
                    v-model="credentials.username"
                    :placeholder="$t('email')"
                    required
                >
                    <template #prepend>
                        <Account />
                    </template>
                    <template #suffix v-if="getFieldError('username')">
                        <el-tooltip placement="top" :content="getFieldError('username')">
                            <InformationOutline class="validation-icon error" />
                        </el-tooltip>
                    </template>
                </el-input>
            </el-form-item>
            <el-form-item prop="password">
                <el-input
                    v-model="credentials.password"
                    size="large"
                    name="password"
                    id="input-password"
                    :placeholder="$t('password')"
                    type="password"
                    showPassword
                    required
                >
                    <template #prepend>
                        <Lock />
                    </template>
                    <template #suffix v-if="getFieldError('password')">
                        <el-tooltip placement="top" :content="getFieldError('password')">
                            <InformationOutline class="validation-icon error" />
                        </el-tooltip>
                    </template>
                </el-input>
            </el-form-item>
            <el-form-item>
                <el-button
                    type="primary"
                    class="w-100"
                    size="large"
                    nativeType="submit"
                    @click.prevent="handleSubmit"
                    :disabled="isLoginDisabled"
                    :loading="isLoading"
                >
                    {{ $t("setup.login") }}
                </el-button>
            </el-form-item>
            <el-form-item>
                <el-button
                    type="default"
                    class="w-100"
                    size="large"
                    @click="openTroubleshootingGuide"
                >
                    {{ $t("setup.troubleshooting") }}
                </el-button>
            </el-form-item>
        </el-form>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed} from "vue"
    import {useRouter, useRoute} from "vue-router"
    import {useI18n} from "vue-i18n"
    import {ElMessage} from "element-plus"
    import type {FormInstance} from "element-plus"
    import axios from "axios"
    import MailChecker from "mailchecker"

    import Account from "vue-material-design-icons/Account.vue"
    import Lock from "vue-material-design-icons/Lock.vue"
    import InformationOutline from "vue-material-design-icons/InformationOutline.vue"
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
    const credentials = ref<Credentials>({
        username: "",
        password: ""
    })

    const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    const PASSWORD_REGEX = /^(?=.*[A-Z])(?=.*\d)\S{8,}$/

    const validateEmail = (_rule: any, value: string, callback: (error?: Error) => void) => {
        if (!value?.trim()) {
            return callback(new Error(t("setup.validation.email_required")));
        } else if (!EMAIL_REGEX.test(value)) {
            return callback(new Error(t("setup.validation.email_invalid")));
        } else if (!MailChecker.isValid(value)) {
            return callback(new Error(t("setup.validation.email_temporary_not_allowed")));
        } else {
            callback();
        }
    };

    const validatePassword = (_rule: any, value: string, callback: (error?: Error) => void) => {
        if (!value || !PASSWORD_REGEX.test(value)) {
            return callback(new Error(t("setup.validation.password_invalid")));
        }
        callback();
    };

    const rules = computed(() => ({
        username: [{required: true, validator: validateEmail, trigger: "blur"}],
        password: [{required: true, validator: validatePassword, trigger: "blur"}]
    }))

    const getFieldError = (fieldName: string) => {
        if (!form.value) return null
        const field = form.value.fields?.find((f: any) => f.prop === fieldName)
        return field?.validateState === "error" ? field.validateMessage : null
    }

    const redirectPath = computed(() => (route.query.from as string) ?? "/welcome")

    const isLoginDisabled = computed(() =>
        !credentials.value.username?.trim() ||
        !credentials.value.password?.trim() ||
        !EMAIL_REGEX.test(credentials.value.username) ||
        !PASSWORD_REGEX.test(credentials.value.password) ||
        !MailChecker.isValid(credentials.value.username) ||
        isLoading.value
    )

    const validateCredentials = async (auth: string) => {
        try {
            document.cookie = `BASIC_AUTH=${auth};path=/;samesite=strict`;
            await axios.get(`${apiUrl()}/usages/all`, {
                timeout: 10000,
                withCredentials: true
            })
        } catch(e) {
            BasicAuth.logout();
            throw e;
        }
    }

    const checkServerInitialization = async () => {
        const response = await axios.get(`${apiUrlWithoutTenants()}/configs`, {
            timeout: 10000,
            withCredentials: true
        })
        return response.data?.isBasicAuthInitialized
    }

    const handleNetworkError = (error: any) => {
        return error.code === "ERR_NETWORK" ||
            error.code === "ECONNREFUSED" ||
            (!error.response && error.message?.includes("Network Error"))
    }

    const loadAuthConfigErrors = async () => {
        try {
            const errors = await miscStore.loadBasicAuthValidationErrors()
            if (errors?.length) {
                errors.forEach((error: string) => {
                    ElMessage.error({
                        message: `${error}. ${t("setup.validation.config_message")}`,
                        duration: 5000,
                        showClose: false
                    })
                })
            } else {
                ElMessage.error({
                    message: t("setup.validation.incorrect_creds")
                })
            }
        } catch {
            ElMessage.error({
                message: t("setup.validation.incorrect_creds")
            })
        }
    }

    const handleSubmit = async () => {
        try {
            coreStore.error = undefined;
            if (!form.value || isLoading.value) return

            if (!(await form.value.validate().catch(() => false))) return

            isLoading.value = true

            const {username, password} = credentials.value

            if (!username?.trim() || !password?.trim()) {
                throw new Error("Username and password are required")
            }

            const trimmedUsername = username.trim()
            const auth = btoa(`${trimmedUsername}:${password}`)

            await validateCredentials(auth)

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
                router.push({name: "setup"})
                return
            }

            if (error?.response?.status === 401) {
                await loadAuthConfigErrors()
            } else if (error?.response?.status === 404) {
                router.push({name: "setup"})
            } else {
                ElMessage.error("Login failed")
            }
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
        width: 100%;
        max-width: 400px;
        padding: 1rem;
        display: flex;
        flex-direction: column;
        justify-content: center;
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

            .validation-icon {
                font-size: 1.25em;
                &.error {
                    color: var(--ks-content-alert);
                }
            }
        }
    }
</style>
