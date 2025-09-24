import {useExecutionsStore} from "../stores/executions";
import {useFlowStore} from "../stores/flow";

export const DASHBOARD_ROUTE = "home";

export const shouldShowWelcome = async () => {
    const executionsStore = useExecutionsStore();
    const flowStore = useFlowStore();
    let executions = 0;

    await flowStore.findFlows({size: 10, sort: "id:asc"})
    await executionsStore.findExecutions({size: 10}).then(response => {
        executions = response?.total;
    })

    return !executions && !flowStore.overallTotal;
};

export const isDashboardRoute = (routeName: string) => {
    return routeName == DASHBOARD_ROUTE;
};