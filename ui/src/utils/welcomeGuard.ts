import {useFlowStore} from "../stores/flow";
import {useExecutionsStore} from "../stores/executions";

export const DASHBOARD_ROUTE = "home";

export const shouldShowWelcome = async () => {
    const flows = await useFlowStore().findFlows({size: 1, onlyTotal: true});
    const executions = await useExecutionsStore().findExecutions({size: 1, onlyTotal: true});

    return !flows && !executions;
};

export const isDashboardRoute = (routeName: string) => routeName == DASHBOARD_ROUTE;
