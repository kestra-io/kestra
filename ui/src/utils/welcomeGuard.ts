import {useAuthStore} from "override/stores/auth"
import {useFlowStore} from "../stores/flow"
import resource from "../models/resource"
import action from "../models/action"
import {TUTORIAL_NAMESPACE} from "./constants"

export const DASHBOARD_ROUTE = "home"

export const shouldShowWelcome = async () => {
    const user = useAuthStore().user
    if (user && !user.hasAnyAction(resource.FLOW, action.LIST)) {
        return false
    }

    const nonTutorialFlows = await useFlowStore().findFlows({
        size: 1,
        onlyTotal: true,
        "filters[namespace][NOT_EQUALS]": TUTORIAL_NAMESPACE,
    })

    return !nonTutorialFlows
}

export const isDashboardRoute = (routeName: string) => routeName == DASHBOARD_ROUTE
