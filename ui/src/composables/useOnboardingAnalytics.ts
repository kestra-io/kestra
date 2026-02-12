import {useRoute} from "vue-router";
import {useApiStore} from "../stores/api";
import {pageFromRoute} from "../utils/eventsRouter";
import {FIRST_FLOW_STEP_IDS, type OnboardingStepType} from "../components/onboarding/guides/firstFlowGuide";

export const ONBOARDING_V2_SEMVER = "2.0.0";
export const ONBOARDING_V2_EXPERIENCE = "first_flow_tutorial";
export const ONBOARDING_V2_TEMPLATE = "first_flow_tutorial";

interface TrackOnboardingOptions {
    action: string;
    mode?: "guided" | "self_serve" | null;
    stepId?: string | null;
    stepType?: OnboardingStepType;
    validationMessage?: string;
    additional?: Record<string, unknown>;
}

export function useOnboardingAnalytics() {
    const apiStore = useApiStore();
    const route = useRoute();

    const trackOnboarding = ({
        action,
        mode,
        stepId,
        stepType,
        validationMessage,
        additional = {},
    }: TrackOnboardingOptions) => {
        const step =
            stepId && FIRST_FLOW_STEP_IDS.includes(stepId)
                ? FIRST_FLOW_STEP_IDS.indexOf(stepId) + 1
                : undefined;

        apiStore.events({
            type: "ONBOARDING",
            onboarding: {
                version: ONBOARDING_V2_SEMVER,
                experience: ONBOARDING_V2_EXPERIENCE,
                template: ONBOARDING_V2_TEMPLATE,
                guideId: "first_flow",
                mode,
                action,
                stepId,
                stepType,
                step,
                validationMessage,
                ...additional,
            },
            page: pageFromRoute(route),
        });
    };

    return {
        trackOnboarding,
    };
}
