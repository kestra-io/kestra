import type {LocationQuery} from "vue-router"

const PARAM_DRIVEN_QUERY_KEYS = [
    "blueprintId",
    "blueprintSource",
    "blueprintSourceYaml",
    "copy",
    "onboarding",
    "onboardingPreset",
    "recipePreset",
    "ai",
    "createTrigger",
]

export function shouldShowLanding(query: LocationQuery): boolean {
    return !PARAM_DRIVEN_QUERY_KEYS.some(key => query[key] !== undefined)
}
