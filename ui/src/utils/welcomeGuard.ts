import {useFlowStore} from "../stores/flow";
import {TUTORIAL_NAMESPACE} from "./constants";

export const DASHBOARD_ROUTE = "home";

export const shouldShowWelcome = async () => {
    const nonTutorialFlows = await useFlowStore().findFlows({
        size: 1,
        onlyTotal: true,
        "filters[namespace][NOT_EQUALS]": TUTORIAL_NAMESPACE,
    });

    return !nonTutorialFlows;
};

export const isDashboardRoute = (routeName: string) => routeName == DASHBOARD_ROUTE;
