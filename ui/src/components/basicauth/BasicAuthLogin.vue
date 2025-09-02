<template>
    <div class="basic-auth-login">
        <div class="d-flex justify-content-center">
            <Logo class="logo" />
        </div>

        <el-form @submit.prevent :model="credentials" ref="form">
            <input type="hidden" name="from" :value="redirectPath">
            <el-form-item>
                <el-input
                    name="username"
                    size="large"
                    id="input-username"
                    v-model="credentials.username"
                    :placeholder="t('email')"
                    required
                    prop="username"
                >
                    <template #prepend>
                        <Account />
                    </template>
                </el-input>
            </el-form-item>
            <el-form-item>
                <el-input
                    v-model="credentials.password"
                    size="large"
                    name="password"
                    id="input-password"
                    :placeholder="t('password')"
                    type="password"
                    show-password
                    required
                    prop="password"
                >
                    <template #prepend>
                        <Lock />
                    </template>
                </el-input>
            </el-form-item>
            <el-form-item>
                <el-button
                    type="primary"
                    class="w-100"
                    size="large"
                    native-type="submit"
                    @click="handleSubmit"
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
    import {ref, computed} from "vue"
    import {useRouter, useRoute} from "vue-router"
    import {useStore} from "vuex"
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
    const store = useStore()
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

    const redirectPath = computed(() => (route.query.from as string) ?? "/welcome")

    const isLoginDisabled = computed(() =>
        !credentials.value.username?.trim() ||
        !credentials.value.password?.trim() ||
        isLoading.value
    )

    const validateCredentials = async (auth: string) => {
        try {
            document.cookie = `BASIC_AUTH=${auth};path=/;samesite=strict`;
            await axios.get(`${apiUrl(store)}/usages/all`, {
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
            (!error.response && error.message.includes("Network Error"))
    }

    const loadAuthConfigErrors = async (showIncorrectCredsMessage = true) => {
        try {
            const errors = await miscStore.loadBasicAuthValidationErrors()
            if (errors && errors.length > 0) {
                errors.forEach((error: string) => {
                    ElMessage.error({
                        message: `${error}. ${t("setup.validation.config_message")}`,
                        duration: 5000,
                        showClose: false
                    })
                })
            } else if (showIncorrectCredsMessage) {
                ElMessage.error(t("setup.validation.incorrect_creds"))
            }
        } catch (error) {
            console.error("Failed to load auth config errors:", error)
        }
    }

    const handleSubmit = async (event: Event) => {
        coreStore.error = undefined;
        event.preventDefault()
        if (!form.value || isLoading.value) return

        if (!(await form.value.validate().catch(() => false))) return

        isLoading.value = true

        try {
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

<style lang="scss" scoped>
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
    }
</style>
