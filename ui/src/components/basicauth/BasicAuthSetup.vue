<template>
    <el-row class="setup-container" :gutter="30" justify="center" align="middle">
        <el-col :xs="24" :md="8" class="setup-sidebar">
            <div class="logo-container">
                <Logo style="width: 14rem;" />
            </div>
            <el-steps :space="60" direction="vertical" :active="activeStep" finishStatus="success">
                <el-step :icon="activeStep > 0 ? CheckBold : AccountPlus" :title="t('setup.steps.user')" :class="{'primary-icon': activeStep <= 0}" />
                <el-step
                    :icon="activeStep > 1 ? CheckBold : MessageOutline"
                    :title="t('setup.steps.survey')"
                    :class="{'primary-icon': activeStep <= 1}"
                />
                <el-step :icon="LightningBolt" :title="t('setup.steps.complete')" class="primary-icon" />
            </el-steps>
        </el-col>
        <el-col :xs="24" :md="16" class="setup-main">
            <el-card class="setup-card">
                <template #header v-if="activeStep !== 2">
                    <div class="card-header">
                        <el-text size="large" class="header-title" v-if="activeStep === 0">
                            {{ t('setup.titles.user') }}
                        </el-text>
                        <el-text size="large" class="header-title" v-else-if="activeStep === 1">
                            {{ t('setup.titles.survey') }}
                        </el-text>
                        <el-text class="d-block mt-4">
                            {{ subtitles[activeStep] }}
                        </el-text>
                        <el-button v-if="activeStep === 1" class="skip-button" @click="handleSurveySkip()">
                            {{ t('setup.survey.skip') }}
                        </el-button>
                    </div>
                </template>

                <div class="setup-card-body">
                    <div v-if="activeStep === 0">
                        <el-form ref="userForm" labelPosition="top" :rules="userRules" :model="formData" :showMessage="false" @submit.prevent="handleUserFormSubmit()">
                            <el-form-item :label="t('setup.form.email')" prop="username">
                                <el-input v-model="userFormData.username" :placeholder="t('setup.form.email')" type="email">
                                    <template #suffix v-if="getFieldError('username')">
                                        <el-tooltip placement="top" :content="getFieldError('username')">
                                            <InformationOutline class="validation-icon error" />
                                        </el-tooltip>
                                    </template>
                                </el-input>
                            </el-form-item>
                            <el-form-item :label="t('setup.form.firstName')" prop="firstName">
                                <el-input v-model="userFormData.firstName" :placeholder="t('setup.form.firstName')">
                                    <template #suffix v-if="getFieldError('firstName')">
                                        <el-tooltip placement="top" :content="getFieldError('firstName')">
                                            <InformationOutline class="validation-icon error" />
                                        </el-tooltip>
                                    </template>
                                </el-input>
                            </el-form-item>
                            <el-form-item :label="t('setup.form.lastName')" prop="lastName">
                                <el-input v-model="userFormData.lastName" :placeholder="t('setup.form.lastName')">
                                    <template #suffix v-if="getFieldError('lastName')">
                                        <el-tooltip placement="top" :content="getFieldError('lastName')">
                                            <InformationOutline class="validation-icon error" />
                                        </el-tooltip>
                                    </template>
                                </el-input>
                            </el-form-item>
                            <el-form-item :label="t('setup.form.password')" prop="password" class="mb-2">
                                <el-input
                                    type="password"
                                    showPassword
                                    v-model="userFormData.password"
                                    :placeholder="t('setup.form.password')"
                                >
                                    <template #suffix v-if="getFieldError('password')">
                                        <el-tooltip placement="top" :content="getFieldError('password')">
                                            <InformationOutline class="validation-icon error" />
                                        </el-tooltip>
                                    </template>
                                </el-input>
                            </el-form-item>
                            <div class="password-requirements mb-4">
                                <el-text>     
                                    {{ t('setup.form.password_requirements') }}
                                </el-text>
                            </div>
                        </el-form>
                        <div class="d-flex gap-1">
                            <el-button type="primary" @click="handleUserFormSubmit()" :disabled="!isUserStepValid">
                                {{ t("setup.confirm.confirm") }}
                            </el-button>
                        </div>
                    </div>

                    <div v-else-if="activeStep === 1">
                        <el-form ref="surveyForm" labelPosition="top" :model="surveyData" :showMessage="false">
                            <el-form-item :label="t('setup.survey.company_size')">
                                <el-radio-group v-model="surveyData.companySize" class="survey-radio-group">
                                    <el-radio
                                        v-for="option in companySizeOptions"
                                        :key="option.value"
                                        :value="option.value"
                                    >
                                        {{ option.label }}
                                    </el-radio>
                                </el-radio-group>
                            </el-form-item>

                            <el-divider class="my-4" />

                            <el-form-item :label="t('setup.survey.use_case')">
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

                            <el-divider class="my-4" />

                            <el-form-item>
                                <el-checkbox v-model="surveyData.newsletter" class="newsletter-checkbox">
                                    <span v-html="t('setup.survey.newsletter')" />
                                </el-checkbox>
                            </el-form-item>
                        </el-form>

                        <div class="d-flex">
                            <el-button type="primary" @click="handleSurveyContinue()">
                                {{ t("setup.survey.continue") }}
                            </el-button>
                        </div>
                    </div>

                    <div v-else-if="activeStep === 2" class="success-step">
                        <img :src="success" alt="success" class="success-img">
                        <div class="success-content">
                            <h1 class="success-title">
                                {{ t('setup.success.title') }}
                            </h1>
                            <p class="success-subtitle">
                                {{ t('setup.success.subtitle') }}
                            </p>
                        </div>
                        <el-button @click="completeSetup()" type="primary" class="success-button">
                            {{ t('setup.steps.complete') }}
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
    import {initPostHogForSetup, trackSetupEvent} from "../../composables/usePosthog"

    import AccountPlus from "vue-material-design-icons/AccountPlus.vue"
    import LightningBolt from "vue-material-design-icons/LightningBolt.vue"
    import MessageOutline from "vue-material-design-icons/MessageOutline.vue"
    import Logo from "../home/Logo.vue"
    import CheckBold from "vue-material-design-icons/CheckBold.vue"
    import InformationOutline from "vue-material-design-icons/InformationOutline.vue"
    import success from "../../assets/success.svg"
    import * as BasicAuth from "../../utils/basicAuth";

    interface UserFormData {
        firstName: string
        lastName: string
        username: string
        password: string
    }

    interface SurveyData {
        companySize: string
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
    const surveyForm: Ref<any> = ref(null)

    const userFormData = ref<UserFormData>({
        firstName: "",
        lastName: "",
        username: "",
        password: ""
    })

    const surveyData = ref<SurveyData>({
        companySize: "",
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

            await initPostHogForSetup(config)

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

    const subtitles = computed(() => [
        t("setup.subtitles.user"),
        t("setup.subtitles.survey"),
    ])

    const companySizeOptions = computed<CompanySizeOption[]>(() => [
        {value: "1-10", label: t("setup.survey.company_1_10")},
        {value: "11-50", label: t("setup.survey.company_11_50")},
        {value: "50-250", label: t("setup.survey.company_50_250")},
        {value: "250+", label: t("setup.survey.company_250_plus")},
        {value: "personal", label: t("setup.survey.company_personal")}
    ])

    const useCaseOptions = computed<CompanySizeOption[]>(() => [
        {value: "infrastructure", label: t("setup.survey.use_case_infrastructure")},
        {value: "business", label: t("setup.survey.use_case_business")},
        {value: "data", label: t("setup.survey.use_case_data")},
        {value: "ml", label: t("setup.survey.use_case_ml")},
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
        firstName: [{required: true, message: t("setup.validation.firstName_required"), trigger: "blur"}],
        lastName: [{required: true, message: t("setup.validation.lastName_required"), trigger: "blur"}],
        password: [{required: true, pattern: PASSWORD_REGEX, message: t("setup.validation.password_invalid"), trigger: "blur"}]
    }))

    const isUserStepValid = computed(() => {
        const data = userFormData.value
        return data.firstName && data.lastName && data.username && data.password &&
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
            await miscStore.addBasicAuth({
                firstName: userFormData.value.firstName,
                lastName: userFormData.value.lastName,
                username: userFormData.value.username,
                password: userFormData.value.password
            })

            BasicAuth.signIn(userFormData.value.username, userFormData.value.password)

            await miscStore.loadConfigs()

            trackSetupEvent("setup_flow:account_created", {
                user_firstname: userFormData.value.firstName,
                user_lastname: userFormData.value.lastName,
                user_email: userFormData.value.username
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
            company_size: surveyData.value.companySize,
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

        trackSetupEvent("setup_flow:completed", {
            user_firstname: userFormData.value.firstName,
            user_lastname: userFormData.value.lastName,
            user_email: userFormData.value.username,
            ...surveySelections
        }, userFormData.value)

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