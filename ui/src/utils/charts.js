import _merge from "lodash/merge";
import State from "./state";

export function tooltip(tooltipModel) {
    const titleLines = tooltipModel.title || [];
    const bodyLines = (tooltipModel.body || []).map(r => r.lines);

    if (tooltipModel.body) {
        let innerHtml = "";

        titleLines.forEach(function (title) {
            innerHtml += "<h6>" + title + "</h6>";
        });

        bodyLines.forEach(function (body, i) {
            if (body.length > 0) {
                let colors = tooltipModel.labelColors[i];
                let style = "background:" + colors.backgroundColor;
                style += "; border-color:" + colors.borderColor;
                let span = "<span class=\"square\" style=\"" + style + "\"></span>";
                innerHtml += span + body + "<br />";
            }
        });

        return innerHtml;
    }

    return undefined;
}

export function defaultConfig(override) {
    return _merge({
        animation: false,
        responsive: true,
        maintainAspectRatio: false,
        layout: {
            padding: {
                top: 2
            }
        },
        scales: {
            x: {
                display: false,
            },
            y: {
                display: false,
            }
        },
        elements: {
            line: {
                borderWidth: 1,
                fill: "start",
                tension: 0.3
            },
            point: {
                radius: 0,
                hoverRadius: 0
            }
        },
        plugins: {
            legend: {
                display: false,
            },
            tooltip: {
                mode: "index",
                intersect: false,
                enabled: false,
            },
        }
    }, override);
}

export function chartClick(moment, router, route, event) {
    const query = {};

    if (event.date) {
        query.startDate = moment(event.date).toISOString(true);
        query.endDate = moment(event.date).add(1, "d").toISOString(true);
    }

    if (event.startDate) {
        query.startDate = moment(event.startDate).toISOString(true);
    }

    if (event.endDate) {
        query.endDate = moment(event.endDate).toISOString(true);
    }

    if (event.status) {
        query.status = event.status.toUpperCase();
    }

    if (event.state) {
        query.state = event.state;
    }

    if (route.query.namespace) {
        query.namespace = route.query.namespace;
    }

    if (route.query.q) {
        query.q = route.query.q;
    }

    if (event.namespace && event.flowId) {
        router.push({
            name: "flows/update",
            params: {
                namespace: event.namespace,
                id: event.flowId,
                tab: "executions",
            },
            query: query
        });
    }

    if (event.namespace) {
        query.namespace = event.namespace;
    }

    router.push({
        name: "executions/list",
        params: {tab: "executions"},
        query: query
    });
}

export function backgroundFromState(state, alpha = 1) {
    const hex = State.color()[state];
    if (!hex) {
        return null;
    }

    const [r, g, b] = hex.match(/\w\w/g).map(x => parseInt(x, 16));
    return `rgba(${r},${g},${b},${alpha})`;
}

export function getFormat(groupBy) {
    switch (groupBy) {
        case "minute":
            return "LT";
        case "hour":
            return "LLL";
        case "day":
            return "l";
        case "week":
            return "DD.MM";
        case "month":
            return "MM.YYYY";
    }
}