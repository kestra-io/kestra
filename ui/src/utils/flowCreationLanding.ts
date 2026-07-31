import type {LocationQuery} from "vue-router"

const PARAM_DRIVEN_QUERY_KEYS = [
    "blank",
    "blueprintId",
    "blueprintSource",
    "blueprintSourceYaml",
    "copy",
    "onboardingPreset",
    "recipePreset",
    "ai",
    "createTrigger",
]

export function shouldShowLanding(query: LocationQuery): boolean {
    return !PARAM_DRIVEN_QUERY_KEYS.some(key => query[key] !== undefined)
}
