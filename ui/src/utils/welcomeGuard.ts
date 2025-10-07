import {useFlowStore} from "../stores/flow";
import {useExecutionsStore} from "../stores/executions";

export const DASHBOARD_ROUTE = "home";

export const shouldShowWelcome = async () => {
    const flowStore = useFlowStore();
    const executionsStore = useExecutionsStore();

    let executions = 0;

    await flowStore.findFlows({size: 10, sort: "id:asc"})
    await executionsStore.findExecutions({size: 10}).then(response => executions = response?.total)

    return !flowStore.overallTotal && !executions;
};

export const isDashboardRoute = (routeName: string) => {
    return routeName == DASHBOARD_ROUTE;
};