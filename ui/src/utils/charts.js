import _merge from "lodash/merge";
import State from "./state";
import Utils from "./utils";
import {cssVariable} from "@kestra-io/ui-libs/src/utils/global";

export function tooltip(tooltipModel) {
    const titleLines = tooltipModel.title || [];
    const bodyLines = (tooltipModel.body || []).map((r) => r.lines);

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
    const color =
        Utils.getTheme() === "dark" ? "#FFFFFF" : cssVariable("--bs-gray-700");

    return _merge(
        {
            animation: false,
            responsive: true,
            maintainAspectRatio: false,
            layout: {
                padding: {
                    top: 2,
                },
            },
            scales: {
                x: {
                    display: false,
                    title: {color},
                    ticks: {color},
                },
                y: {
                    display: false,
                    title: {color},
                    ticks: {color},
                },
                yB: {
                    display: false,
                    title: {color},
                    ticks: {color},
                },
            },
            elements: {
                line: {
                    borderWidth: 1,
                    fill: "start",
                    tension: 0.3,
                },
                point: {
                    radius: 0,
                    hoverRadius: 0,
                },
            },
            plugins: {
                legend: {
                    display: false,
                },
                tooltip: {
                    mode: "index",
                    intersect: false,
                    enabled: false,
                    boxPadding: 5,
                    usePointStyle: true,
                    multiKeyBackground: "#000000",
                },
            },
        },
        override,
    );
}

export function chartClick(moment, router, route, event) {
    const query = {};

    if (event.date) {
        const formattedDate = moment(
            event.date,
            moment.localeData().longDateFormat("L"),
        );
        query.startDate = formattedDate.toISOString(true);
        query.endDate = formattedDate.add(1, "d").toISOString(true);
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
                tenant: route.params.tenant,
            },
            query: query,
        });
    } else {
        if (event.namespace) {
            query.namespace = event.namespace;
        }

        router.push({
            name: "executions/list",
            params: {
                tenant: route.params.tenant,
            },
            query: query,
        });
    }
}

export function backgroundFromState(state, alpha = 1) {
    const hex = State.color()[state];
    if (!hex) {
        return null;
    }

    const [r, g, b] = hex.match(/\w\w/g).map((x) => parseInt(x, 16));
    return `rgba(${r},${g},${b},${alpha})`;
}

export function getConsistentHEXColor(value) {
    const hex = State.color()[value];

    if (hex) return hex;

    // FNV-1a Hash Algorithm
    let hash = 0x811c9dc5; // FNV offset basis (32-bit)
    const fnvPrime = 0x01000193; // FNV prime (32-bit)

    for (let i = 0; i < value.length; i++) {
        hash ^= value.charCodeAt(i); // XOR with character code
        hash = (hash * fnvPrime) >>> 0; // Multiply by FNV prime and ensure 32-bit
    }

    // Bit-mixing step (to ensure greater differentiation)
    hash ^= hash >>> 16; // XOR with a shifted version
    hash *= 0x85ebca6b; // Multiply with a large prime
    hash ^= hash >>> 13; // XOR again with another shift
    hash *= 0xc2b2ae35; // Multiply with another large prime
    hash ^= hash >>> 16; // Final XOR with a shift

    // Generate a HEX color from the hash
    return `#${((hash >>> 0) & 0xffffff).toString(16).padStart(6, "0")}`;
}

export function getStateColor(state) {
    return State.getStateColor(state);
}

export function getFormat(groupBy) {
    switch (groupBy) {
        case "minute":
            return "LT";
        case "hour":
            return "LLL";
        case "day":
        case "week":
            return "l";
        case "month":
            return "MM.YYYY";
    }
}
