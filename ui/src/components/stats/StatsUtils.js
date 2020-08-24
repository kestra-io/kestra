import {assign, clone} from "lodash";
import moment from "moment";

export function dateFill(data, startDate, endDate, property, dateFormat, emptyMetric) {
    const dateRange = moment.range(startDate, endDate);

    let resultMetrics = [];

    for (let day of dateRange.by("days")) {
        const d = day.format(dateFormat);

        let currentEmpty = clone(emptyMetric);
        currentEmpty[property] = d;

        let realMetric = data.filter(element => element[property] === d); // jshint ignore:line

        if (realMetric.length > 0) {
            let metrics = assign.apply(
                null,
                [currentEmpty].concat(realMetric)
            );
            resultMetrics.push(metrics);
        } else {
            resultMetrics.push(currentEmpty);
        }
    }

    return resultMetrics;
}

export function tooltipPosition (data, width, height) {
    return {top: -(height/3), left: -width};
}
