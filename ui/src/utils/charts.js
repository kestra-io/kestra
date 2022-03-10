import _merge from "lodash/merge";

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
            xAxes: {
                display: false,
            },
            yAxes: {
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

export function chartClick(self, event) {
    const query = {};

    if (event.date) {
        query.start = self.$moment(event.date).toISOString(true);
        query.end = self.$moment(event.date).add(1, "d").toISOString(true);
    }

    if (event.startDate) {
        query.start = self.$moment(event.startDate).toISOString(true);
    }

    if (event.endDate) {
        query.end = self.$moment(event.endDate).toISOString(true);
    }

    if (event.status) {
        query.status = event.status.toUpperCase();
    }

    if (self.$route.query.namespace) {
        query.namespace = self.$route.query.namespace;
    }

    if (self.$route.query.q) {
        query.q = self.$route.query.q;
    }

    if (event.namespace && event.flowId) {
        self.$router.push({
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

    self.$router.push({
        name: "executions/list",
        params: {tab: "executions"},
        query: query
    });
}