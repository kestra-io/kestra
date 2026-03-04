<template>
    <el-row class="setup-container" :gutter="30" justify="center" align="middle">
        <el-col :xs="24" :md="8" class="setup-sidebar">
            <div class="logo-container">
                <Logo style="width: 14rem;" />
            </div>
            <el-steps :space="60" direction="vertical" :active="activeStep" finishStatus="success">
                <el-step :icon="activeStep > 0 ? CheckBold : AccountPlus" :title="$t('setup.steps.user')" :class="{'primary-icon': activeStep <= 0}" />
                <el-step
                    :icon="activeStep > 1 ? CheckBold : MessageOutline"
                    :title="$t('setup.steps.survey')"
                    :class="{'primary-icon': activeStep <= 1}"
                />
                <el-step :icon="LightningBolt" :title="$t('setup.steps.complete')" class="primary-icon" />
            </el-steps>
        </el-col>
        <el-col :xs="24" :md="16" class="setup-main">
            <el-card class="setup-card">
                <template #header v-if="activeStep !== 2">
                    <div class="card-header">
                        <el-text size="large" class="header-title" v-if="activeStep === 0">
                            {{ $t('setup.titles.user') }}
                        </el-text>
                        <el-text size="large" class="header-title" v-else-if="activeStep === 1">
                            {{ $t('setup.titles.survey') }}
                        </el-text>
                        <el-text v-if="activeStep === 0" class="header-subtitle">
                            {{ $t('setup.subtitles.user') }}
                        </el-text>
                        <el-button v-if="activeStep === 1" class="skip-button" @click="handleSurveySkip()">
                            {{ $t('setup.survey.skip') }}
                        </el-button>
                    </div>
                </template>

                <div class="setup-card-body">
                    <div v-if="activeStep === 0">
                        <el-form ref="userForm" labelPosition="top" :rules="userRules" :model="formData" :showMessage="false" @submit.prevent="handleUserFormSubmit()">
                            <el-form-item :label="$t('setup.form.email')" prop="username" class="mb-2">
                                <el-input v-model="userFormData.username" placeholder="admin@company.com" type="email">
                                    <template #suffix v-if="getFieldError('username')">
                                        <el-tooltip placement="top" :content="getFieldError('username')">
                                            <InformationOutline class="validation-icon error" />
                                        </el-tooltip>
                                    </template>
                                </el-input>
                            </el-form-item>
                            <div class="username-requirements mb-2">
                                <el-text>
                                    Used as your admin login. No emails unless you opt in.
                                </el-text>
                            </div>
                            <el-form-item :label="$t('setup.form.password')" prop="password" class="mb-2">
                                <el-input
                                    type="password"
                                    showPassword
                                    v-model="userFormData.password"
                                    placeholder="StrongPass1"
                                >
                                    <template #suffix v-if="getFieldError('password')">
                                        <el-tooltip placement="top" :content="getFieldError('password')">
                                            <InformationOutline class="validation-icon error" />
                                        </el-tooltip>
                                    </template>
                                </el-input>
                            </el-form-item>
                            <div class="password-requirements mb-2">
                                <el-text>
                                    {{ $t('setup.form.password_requirements') }}
                                </el-text>
                            </div>
                        </el-form>
                        <div class="d-flex justify-content-end gap-1">
                            <el-button type="primary" @click="handleUserFormSubmit()" :disabled="!isUserStepValid">
                                {{ $t("setup.confirm.confirm") }}
                            </el-button>
                        </div>
                    </div>

                    <div v-else-if="activeStep === 1">
                        <el-form ref="surveyForm" labelPosition="top" :model="surveyData" :showMessage="false">
                            <el-form-item :label="$t('setup.survey.company_size')">
                                <el-radio-group v-model="surveyData.mainGoal" class="survey-radio-group">
                                    <el-radio
                                        v-for="option in intentOptions"
                                        :key="option.value"
                                        :value="option.value"
                                    >
                                        {{ option.label }}
                                    </el-radio>
                                </el-radio-group>
                            </el-form-item>

                            <el-divider class="survey-divider" />

                            <el-form-item :label="$t('setup.survey.use_case')">
                                <div class="use-case-checkboxes">
                                    <el-checkbox-group v-model="surveyData.useCases">
                                        <el-checkbox
                                            v-for="option in useCaseOptions"
                                            :key="option.value"
                                            :value="option.value"
                                            class="survey-checkbox"
                                        >
                                            {{ option.label }}
                                        </el-checkbox>
                                    </el-checkbox-group>
                                </div>
                            </el-form-item>

                            <el-divider class="survey-divider" />

                            <el-form-item :label="$t('setup.survey.newsletter_heading')" class="newsletter-form-item">
                                <el-checkbox v-model="surveyData.newsletter" class="newsletter-checkbox">
                                    {{ $t('setup.survey.newsletter') }}
                                </el-checkbox>
                            </el-form-item>
                        </el-form>

                        <div class="d-flex justify-content-end">
                            <el-button type="primary" @click="handleSurveyContinue()">
                                {{ $t("setup.survey.continue") }}
                            </el-button>
                        </div>
                    </div>

                    <div v-else-if="activeStep === 2" class="success-step">
                        <img :src="success" alt="success" class="success-img">
                        <div class="success-content">
                            <h1 class="success-title">
                                {{ $t('setup.success.title') }}
                            </h1>
                            <p class="success-subtitle">
                                {{ $t('setup.success.subtitle') }}
                            </p>
                        </div>
                        <el-button @click="completeSetup()" type="primary" class="success-button">
                            {{ $t('setup.steps.complete') }}
                        </el-button>
                    </div>
                </div>
            </el-card>
        </el-col>
    </el-row>
</template>

<script setup lang="ts">
    import MailChecker from "mailchecker"
    import {ref, computed, onUnmounted, type Ref} from "vue"
    import {useRouter} from "vue-router"
    import {useI18n} from "vue-i18n"
    import {useMiscStore} from "override/stores/misc"
    import {useSurveySkip} from "../../composables/useSurveyData"
    import {trackSetupEvent} from "../../composables/usePosthog"
    import {identifyPosthogUser, initPosthogIfEnabled} from "../../utils/posthog"

    import AccountPlus from "vue-material-design-icons/AccountPlus.vue"
    import LightningBolt from "vue-material-design-icons/LightningBolt.vue"
    import MessageOutline from "vue-material-design-icons/MessageOutline.vue"
    import Logo from "../home/Logo.vue"
    import CheckBold from "vue-material-design-icons/CheckBold.vue"
    import InformationOutline from "vue-material-design-icons/InformationOutline.vue"
    import success from "../../assets/success.svg"
    import * as BasicAuth from "../../utils/basicAuth";

    interface UserFormData {
        username: string
        password: string
    }

    interface SurveyData {
        mainGoal: string
        useCases: string[]
        newsletter: boolean
    }

    interface CompanySizeOption {
        value: string
        label: string
    }

    const {t} = useI18n()
    const router = useRouter()
    const miscStore = useMiscStore()
    const {storeSurveySkipData} = useSurveySkip()

    const activeStep = ref(0)
    const userForm: Ref<any> = ref(null)

    const userFormData = ref<UserFormData>({
        username: "",
        password: ""
    })

    const surveyData = ref<SurveyData>({
        mainGoal: "",
        useCases: [],
        newsletter: false
    })

    const formData = computed(() => userFormData.value)

    const initializeSetup = async () => {
        try {
            const config = await miscStore.loadConfigs()

            if (config?.isBasicAuthInitialized) {
                localStorage.removeItem("basicAuthSetupInProgress")
                localStorage.removeItem("setupStartTime")
                router.push({name: "login"})
                return
            }

            await initPosthogIfEnabled(config)

            trackSetupEvent("setup_flow:started", {
                setup_step: "account_creation"
            }, userFormData.value)

            localStorage.setItem("basicAuthSetupInProgress", "true")
            localStorage.setItem("setupStartTime", Date.now().toString())
        } catch {
            /* Silently handle config loading errors */
        }
    }

    initializeSetup()

    onUnmounted(() => {
        if (localStorage.getItem("basicAuthSetupCompleted") !== "true") {
            localStorage.removeItem("basicAuthSetupInProgress")
        }
    })

    const intentOptions = computed<CompanySizeOption[]>(() => [
        {value: "learning_exploring", label: t("setup.survey.company_1_10")},
        {value: "personal_project", label: t("setup.survey.company_11_50")},
        {value: "evaluating_team_company", label: t("setup.survey.company_50_250")},
        {value: "production_use", label: t("setup.survey.company_250_plus")}
    ])

    const useCaseOptions = computed<CompanySizeOption[]>(() => [
        {value: "infrastructure", label: t("setup.survey.use_case_infrastructure")},
        {value: "data", label: t("setup.survey.use_case_data")},
        {value: "ml", label: t("setup.survey.use_case_ml")},
        {value: "business", label: t("setup.survey.use_case_business")},
        {value: "scheduling", label: t("setup.survey.use_case_scheduling")},
        {value: "other", label: t("setup.survey.use_case_other")}
    ])

    const EMAIL_REGEX = /^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$/
    const PASSWORD_REGEX = /^(?=.*[A-Z])(?=.*\d)\S{8,}$/

    const validateEmail = (_rule: any, value: string, callback: (error?: Error) => void) => {
        if (!value) {
            callback(new Error(t("setup.validation.email_required")))
            return
        }

        if (!EMAIL_REGEX.test(value)) {
            callback(new Error(t("setup.validation.email_invalid")))
            return
        }

        if (!MailChecker.isValid(value)) {
            callback(new Error(t("setup.validation.email_temporary_not_allowed")))
            return
        }

        callback()
    }

    const userRules = computed(() => ({
        username: [{required: true, validator: validateEmail, trigger: "blur"}],
        password: [{required: true, pattern: PASSWORD_REGEX, message: t("setup.validation.password_invalid"), trigger: "blur"}]
    }))

    const isUserStepValid = computed(() => {
        const data = userFormData.value
        return data.username && data.password &&
            EMAIL_REGEX.test(data.username) && PASSWORD_REGEX.test(data.password) &&
            MailChecker.isValid(data.username)
    })

    const getFieldError = (fieldName: string) => {
        if (!userForm.value) return null
        const field = userForm.value.fields?.find((f: any) => f.prop === fieldName)
        return field?.validateState === "error" ? field.validateMessage : null
    }

    const handleUserFormSubmit = async () => {
        try {
            const normalizedEmail = userFormData.value.username.trim()

            await miscStore.addBasicAuth({
                username: normalizedEmail,
                password: userFormData.value.password
            })

            BasicAuth.signIn(normalizedEmail, userFormData.value.password)

            const configs = await miscStore.loadConfigs()

            await identifyPosthogUser(configs, {email: normalizedEmail})

            trackSetupEvent("setup_flow:account_created", {
                user_email: normalizedEmail
            }, userFormData.value)


            localStorage.setItem("basicAuthUserCreated", "true")

            activeStep.value = 1
        } catch (error: any) {
            trackSetupEvent("setup_flow:account_creation_failed", {
                error_message: error.message || "Unknown error"
            }, userFormData.value)
            console.error("Failed to create basic auth account:", error)
        }
    }

    const handleSurveyContinue = () => {
        localStorage.setItem("basicAuthSurveyData", JSON.stringify(surveyData.value))

        const surveySelections: Record<string, any> = {
            main_goal: surveyData.value.mainGoal,
            use_cases: surveyData.value.useCases,
            use_cases_count: surveyData.value.useCases.length,
            newsletter_opted_in: surveyData.value.newsletter,
            survey_action: "submitted"
        }

        if (surveyData.value.useCases.length > 0) {
            surveyData.value.useCases.forEach(useCase => {
                surveySelections[`use_case_${useCase}`] = true
            })
        }

        trackSetupEvent("setup_flow:marketing_survey_submitted", {
            ...surveySelections
        }, userFormData.value)

        activeStep.value = 2
    }

    const handleSurveySkip = () => {
        const surveySelections: Record<string, any> = {
            main_goal: surveyData.value.mainGoal,
            newsletter_opted_in: surveyData.value.newsletter,
            survey_action: "skipped"
        }

        if (surveyData.value.useCases.length > 0) {
            surveyData.value.useCases.forEach(useCase => {
                surveySelections[`use_case_${useCase}`] = true
            })
        }

        storeSurveySkipData({
            ...surveySelections
        })

        trackSetupEvent("setup_flow:marketing_survey_skipped", {
            ...surveySelections
        }, userFormData.value)

        activeStep.value = 2
    }

    const completeSetup = () => {
        const savedSurveyData = localStorage.getItem("basicAuthSurveyData")
        const surveySelections = savedSurveyData ? JSON.parse(savedSurveyData) : {}
        const normalizedEmail = userFormData.value.username.trim()

        const completeEventPayload: Record<string, any> = {
            user_email: normalizedEmail,
            newsletter_opted_in: surveyData.value.newsletter,
            ...surveySelections
        }

        trackSetupEvent("setup_flow:completed", completeEventPayload, userFormData.value)

        localStorage.setItem("basicAuthSetupCompleted", "true")
        localStorage.removeItem("basicAuthSetupInProgress")
        localStorage.removeItem("setupStartTime")
        localStorage.removeItem("basicAuthSurveyData")
        localStorage.removeItem("basicAuthUserCreated")
        localStorage.setItem("basicAuthSetupCompletedAt", new Date().toISOString())

        router.push({name: "welcome"})
    }
</script>

<style src="./setup.scss" scoped lang="scss" />
