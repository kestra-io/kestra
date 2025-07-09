<template>
    <div class="setup-container">
        <div class="setup-sidebar">
            <div class="logo-container">
                <Logo style="width: 14rem;" />
            </div>
            <el-steps :space="60" direction="vertical" :active="activeStep" finish-status="success">
                <el-step :icon="activeStep > 0 ? CheckBold : AccountPlus" :title="t('setup.steps.user')" />
                <el-step
                    :icon="activeStep > 1 ? CheckBold : Cogs"
                    :title="t('setup.steps.config')"
                />
                <el-step
                    :icon="activeStep > 2 ? CheckBold : MessageOutline"
                    :title="t('setup.steps.survey')"
                />
                <el-step :icon="LightningBolt" :title="t('setup.steps.complete')" />
            </el-steps>
        </div>
        <div class="setup-main">
            <div class="setup-card-header">
                <div class="card-header">
                    <el-text size="large" class="header-title" v-if="activeStep === 0">
                        {{ t('setup.titles.user') }}
                    </el-text>
                    <el-text size="large" class="header-title" v-else-if="activeStep === 1">
                        Welcome {{ userFormData.firstName }}
                    </el-text>
                    <el-text size="large" class="header-title" v-else-if="activeStep === 2">
                        {{ t('setup.titles.survey') }}
                    </el-text>
                    <el-text class="d-block mt-4">
                        {{ subtitles[activeStep] }}
                    </el-text>
                    <el-button v-if="activeStep === 2" class="skip-button" @click="handleSurveySkip()">
                        {{ t('setup.survey.skip') }}
                    </el-button>
                </div>
            </div>
            
            <div class="setup-card-body">
                <div v-if="activeStep === 0">
                    <el-form ref="userForm" label-position="top" :rules="userRules" :model="formData" :show-message="false" @submit.prevent="handleUserFormSubmit()">
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
                                show-password
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
                                8+ chars, 1 upper, 1 number
                            </el-text>
                        </div>
                    </el-form>
                    <div class="d-flex gap-1">
                        <el-button type="primary" @click="handleUserFormSubmit()" :disabled="!isUserStepValid">
                            {{ t("setup.confirm.confirm") }}
                        </el-button>
                    </div>
                </div>
                
                <div class="d-flex flex-column gap-4" v-else-if="activeStep === 1">
                    <el-card v-if="isLoading">
                        <el-text>Loading configuration...</el-text>
                    </el-card>
                    <el-card v-else-if="setupConfigurationLines.length > 0">
                        <el-row
                            v-for="config in setupConfigurationLines"
                            :key="config.name"
                            class="lh-lg mt-1 mb-1 align-items-center gap-2"
                        >
                            <component :is="config.icon" />
                            <el-text size="small">
                                {{ t("setup.config." + config.name) }}
                            </el-text>
                            <el-divider class="m-auto" />
                            <Check class="text-success" v-if="config.value === true" />
                            <Close class="text-danger" v-else-if="config.value === false" />
                            <el-text v-else size="small">
                                {{ (config.value ?? "NOT SETUP").toString().capitalize() }}
                            </el-text>
                        </el-row>
                    </el-card>
                    <el-card v-else>
                        <el-text>No configuration data available</el-text>
                    </el-card>
                    <el-text class="align-self-start">
                        {{ t("setup.confirm.config_title") }}
                    </el-text>
                    <div class="d-flex align-self-start">
                        <el-button @click="previousStep()">
                            {{ t("setup.confirm.not_valid") }}
                        </el-button>
                        <el-button type="primary" @click="initBasicAuth()">
                            {{ t("setup.confirm.valid") }}
                        </el-button>
                    </div>
                </div>
                
                <div v-else-if="activeStep === 2">
                    <el-form ref="surveyForm" label-position="top" :model="surveyData" :show-message="false">
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
                
                <div v-else-if="activeStep === 3" class="success-step">
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
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, onUnmounted, type Ref} from "vue"
    import {useRouter} from "vue-router"
    import {useI18n} from "vue-i18n"
    import MailChecker from "mailchecker"
    import {useMiscStore} from "../../stores/misc"
    import {useApiStore} from "../../stores/api"
    import {useSurveySkip} from "../../composables/useSurveyData"

    import Cogs from "vue-material-design-icons/Cogs.vue"
    import AccountPlus from "vue-material-design-icons/AccountPlus.vue"
    import LightningBolt from "vue-material-design-icons/LightningBolt.vue"
    import MessageOutline from "vue-material-design-icons/MessageOutline.vue"
    import Logo from "../home/Logo.vue"
    import Check from "vue-material-design-icons/Check.vue"
    import Close from "vue-material-design-icons/Close.vue"
    import CheckBold from "vue-material-design-icons/CheckBold.vue"
    import InformationOutline from "vue-material-design-icons/InformationOutline.vue"
    import Database from "vue-material-design-icons/Database.vue"
    import CurrentDc from "vue-material-design-icons/CurrentDc.vue"
    import CloudOutline from "vue-material-design-icons/CloudOutline.vue"
    import Lock from "vue-material-design-icons/Lock.vue"
    import success from "../../assets/success.svg"

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

    interface ConfigLine {
        name: string
        icon: any
        value: any
    }

    interface CompanySizeOption {
        value: string
        label: string
    }

    const miscStore = useMiscStore()
    const apiStore = useApiStore()
    const router = useRouter()
    const {t} = useI18n()
    const {storeSurveySkipData} = useSurveySkip()

    const activeStep = ref(0)
    const usageData = ref<any>(null)
    const isLoading = ref(true)
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
    const setupConfiguration = computed(() => usageData.value?.configurations ?? {})

    const trackSetupEvent = (eventName: string, additionalData: Record<string, any> = {}) => {
        const configs = miscStore.getConfigs
        const uid = localStorage.getItem("uid")
    
        if (!configs || !uid || configs.isAnonymousUsageEnabled === false) return
    
        const userInfo = activeStep.value >= 1 ? {
            user_firstname: userFormData.value.firstName || undefined,
            user_lastname: userFormData.value.lastName || undefined,
            user_email: userFormData.value.username || undefined
        } : {}
    
        apiStore.posthogEvents({
            type: eventName,
            setup_step_current: activeStep.value,
            instance_id: configs.uuid,
            user_id: uid,
            ...userInfo,
            ...additionalData
        })
    }

    const initializeSetup = async () => {
        try {
            const config = await miscStore.loadConfigs()
            
            if (config && config.isBasicAuthInitialized && localStorage.getItem("basicAuthSetupCompleted") === "true") {
                localStorage.removeItem("basicAuthSetupInProgress")
                localStorage.removeItem("setupStartTime")
                router.push({name: "welcome"})
                return
            }
            
            localStorage.setItem("basicAuthSetupInProgress", "true")
            localStorage.setItem("setupStartTime", Date.now().toString())
            
            if (config && config.isBasicAuthInitialized) {
                activeStep.value = 1
            }
            
            usageData.value = await miscStore.loadAllUsages()
            trackSetupEvent("setup_flow:started", {step_number: 1})
        } catch {
            /* Silently handle usage data loading errors */
        } finally {
            isLoading.value = false
        }
    }

    initializeSetup()

    onUnmounted(() => {
        if (localStorage.getItem("basicAuthSetupCompleted") !== "true") {
            localStorage.removeItem("basicAuthSetupInProgress")
        }
    })

    const setupConfigurationLines = computed<ConfigLine[]>(() => {
        if (!setupConfiguration.value) return []
        const configs = miscStore.getConfigs
        return [
            {name: "repository", icon: Database, value: setupConfiguration.value.repositoryType},
            {name: "queue", icon: CurrentDc, value: setupConfiguration.value.queueType},
            {name: "storage", icon: CloudOutline, value: setupConfiguration.value.storageType},
            {name: "basicauth", icon: Lock, value: activeStep.value >= 1 ? true : configs?.isBasicAuthInitialized}
        ]
    })

    const subtitles = computed(() => [
        t("setup.subtitles.user"),
        t("setup.subtitles.config"),
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
    const PASSWORD_REGEX = /^(?=.*[A-Z])(?=.*\d).{8,}$/

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

    const nextStep = () => {
        const currentStep = activeStep.value
        activeStep.value++
        trackSetupEvent("setup_flow:step_advanced", {from_step_number: currentStep, to_step_number: activeStep.value})
    }

    const previousStep = () => {
        const currentStep = activeStep.value
        activeStep.value--
        trackSetupEvent("setup_flow:step_back", {from_step_number: currentStep, to_step_number: activeStep.value})
    }

    const handleUserFormSubmit = async () => {
        try {
            await miscStore.addBasicAuth({
                firstName: userFormData.value.firstName,
                lastName: userFormData.value.lastName,
                username: userFormData.value.username,
                password: userFormData.value.password
            })
            
            const credentials = btoa(`${userFormData.value.username}:${userFormData.value.password}`)
            localStorage.setItem("basicAuthCredentials", credentials)
            
            await miscStore.loadConfigs()
            
            try {
                usageData.value = await miscStore.loadAllUsages()
            } catch {
                /* Silently handle usage data loading  */
            }
            
            trackSetupEvent("setup_flow:account_created", {
                user_firstname: userFormData.value.firstName,
                user_lastname: userFormData.value.lastName,
                user_email: userFormData.value.username
            })
            
            trackSetupEvent("setup_flow:user_form_submitted", {is_form_valid: isUserStepValid.value})
            nextStep()
        } catch (error: any) {
            trackSetupEvent("setup_flow:account_creation_failed", {
                error_message: error.message || "Unknown error"
            })
            console.error("Failed to create basic auth account:", error)
        }
    }

    const initBasicAuth = () => {
        nextStep()
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
            step_number: 3,
            ...surveySelections
        })
    
        nextStep()
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
            step_number: 3,
            ...surveySelections
        })
    
        trackSetupEvent("setup_flow:marketing_survey_skipped", {
            step_number: 3,
            ...surveySelections
        })
    
        nextStep()
    }

    const completeSetup = () => {
        const savedSurveyData = localStorage.getItem("basicAuthSurveyData")
        const surveySelections = savedSurveyData ? JSON.parse(savedSurveyData) : {}    

        trackSetupEvent("setup_flow:completed", {
            user_firstname: userFormData.value.firstName,
            user_lastname: userFormData.value.lastName,
            user_email: userFormData.value.username,
            ...surveySelections
        })
    
        localStorage.setItem("basicAuthSetupCompleted", "true")
        localStorage.removeItem("basicAuthSetupInProgress")
        localStorage.removeItem("setupStartTime")
        localStorage.removeItem("basicAuthSurveyData")
        localStorage.setItem("basicAuthSetupCompletedAt", new Date().toISOString())
    
        router.push({name: "login"})
    }
</script>

<style scoped lang="scss">
$mobile-breakpoint: 992px;

.setup-container {
    display: grid;
    grid-template-columns: 1fr;
    width: 100%;
    max-width: 911px;
    gap: 32px;
    margin: 0 auto;
    padding: 20px;
    align-items: start;
    
    @media (min-width: $mobile-breakpoint) {
        grid-template-columns: 219px 564px;
        width: 911px;
        height: 587px;
        gap: 128px;
        padding: 0;
        align-items: center;
    }
}

.setup-sidebar {
    width: 100%;
    border-radius: 11.23px;
    gap: 32px;
    padding: 24px;
    box-shadow: 0 4.21px 28.08px var(--Shadows);
    display: flex;
    flex-direction: column;
    
    @media (min-width: $mobile-breakpoint) {
        width: 219px;
        height: 432px;
        padding: 0;
    }
    
    .logo-container {
        padding-bottom: 24px;
        
        @media (min-width: $mobile-breakpoint) {
            padding-bottom: 32px;
        }
    }
}

.setup-main {
    width: 100%;
    border-radius: 8px;
    gap: 2rem;
    padding: 24px;
    background: var(--ks-background-card);
    border: 1px solid var(--ks-border-primary);
    box-shadow: 0 2px 4px var(--ks-card-shadow);
    display: flex;
    flex-direction: column;
    
    @media (min-width: $mobile-breakpoint) {
        padding: 2rem;
    }
}

.setup-card {
    min-width: 100%;
    
    &-body {
        flex: 1;
        display: flex;
        flex-direction: column;
        justify-content: center;
    }
    
    @media (min-width: $mobile-breakpoint) {
        min-width: 800px;
    }
}

.el-step {
    :deep(.el-step__head) {
        &, & > .el-step__icon {
            width: 43px !important;
        }

        & > .el-step__icon {
            height: 43px !important;
        }

        .el-step__line {
            left: 21px;
        }
    }

    :deep(.el-step__title) {
        padding: 0;
        vertical-align: middle;
        line-height: 43px;
        color: var(--ks-content-inactive);
        
        &.is-process {
            color: var(--ks-content-primary);
            font-weight: 400;
            font-size: 16px;
        }
    }
}

.card-header {
    position: relative;
}

.skip-button {
    position: absolute;
    top: 0;
    right: 0;
    color: var(--ks-content-primary);
    font-size: 14px;
    font-weight: 400;
    
    &:hover {
        color: var(--ks-content-secondary);
    }
}

.header-title {
    color: var(--ks-content-primary);
    font-weight: 600;
    font-size: 24px;
    line-height: 36px;
}

.password-requirements {
    margin-top: -8px;
    
    .el-text {
        color: var(--ks-content-tertiary);
        font-size: 14px;
    }
}

.survey-radio-group {
    display: flex;
    gap: 1rem;
    margin-top: 1rem;

    :deep(.el-radio) {
        margin: 0 !important;

        .el-radio__label {
            font-size: 14px;
            color: var(--ks-content-primary);
        }

        .el-radio__inner {
            width: 24px;
            height: 24px;
            border: 2px solid var(--ks-border-primary);
            background: transparent;

            &::after {
                width: 12px;
                height: 12px;
                background-color: var(--ks-button-background-primary);
            }
        }

        &.is-checked .el-radio__inner {
            border-color: var(--ks-button-background-primary);
            background: transparent;
        }
    }
}

.use-case-checkboxes {
    margin-top: 10px;
    
    :deep(.el-checkbox-group) {
        display: flex;
        flex-wrap: wrap;
        gap: 16px 40px;
    }
    
    .survey-checkbox {
        display: flex;
        align-items: center;
        border: none;
        background-color: transparent;
        cursor: pointer;
        margin: 0;
    }
}

.newsletter-checkbox {
    margin-top: 16px;
    display: flex;
    align-items: center;
    
    :deep(.el-checkbox__label) {
        padding-left: 8px;
    }
}

.survey-checkbox, .newsletter-checkbox {
    :deep(.el-checkbox__input) {
        margin-right: 8px;
        align-self: center;
        
        .el-checkbox__inner {
            border: 2px solid #918BA9;
            background-color: transparent;
            width: 18px;
            height: 18px;
            position: relative;
            
            &::after {
                content: "";
                position: absolute;
                border: 2px solid white;
                border-top: none;
                border-left: none;
                width: 4px;
                height: 8px;
                transform: rotate(45deg);
                opacity: 0;
                top: 1px;
                left: 4px;
            }
        }
        
        &.is-checked .el-checkbox__inner {
            border-color: #8405FF;
            background-color: #8405FF;
            
            &::after {
                opacity: 1;
            }
        }
    }
    
    :deep(.el-checkbox__label) {
        color: #e2e8f0;
        font-size: 14px;
        padding-left: 0;
        line-height: 1.4;
        align-self: center;
    }
}

.success-step {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    text-align: center;
    
    .success-img {
        width: 65%;
        margin-top: -8rem;
    }
    
    .success-content {
        margin-top: -8rem;
        position: relative;
        padding: 2rem;
    }
    
    .success-title {
        font-weight: 600;
        font-size: 24px;
        line-height: 36px;
        color: var(--ks-content-primary);
        margin: 0;
    }
    
    .success-subtitle {
        font-weight: 600;
        font-size: 18.4px;
        line-height: 28px;
        color: var(--ks-content-primary);
        margin: 0;
    }
    
    .success-button {
        margin-top: 16px;
    }
}

:deep(.el-button:not(.skip-button)) {
    margin-top: 1rem;
}

:deep(.el-card__body) {
    display: flex;
    flex-direction: column;
    gap: calc(var(--spacer) / 2);
}

:deep(.el-form-item.is-error .el-input__wrapper) {
    box-shadow: 0 0 0 1px var(--ks-border-error) inset;
}

:deep(.el-form-item.is-error .el-input__suffix-inner) {
    color: var(--ks-content-alert);
}

:deep(.el-form-item__error) {
    color: var(--ks-content-alert) !important;
}

:deep(.el-input__inner) {
    font-size: 14px;

    &::placeholder {
        color: var(--ks-content-tertiary) !important;
    }
}

.el-row {
    .el-divider {
        flex: 1;
    }
    
    .el-col .el-card:deep(.el-card__header) {
        border-bottom: 0;
    }
}

html.dark .el-col .el-card * {
    color: var(--ks-content-primary);
}
</style>