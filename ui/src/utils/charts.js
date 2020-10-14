import _merge from "lodash/merge";

export function tooltip(tooltipModel) {
    const titleLines = tooltipModel.title || [];
    const bodyLines = (tooltipModel.body || []).map(r => r.lines);

    if (tooltipModel.body) {
        let innerHtml = '';

        titleLines.forEach(function (title) {
            innerHtml += '<h6>' + title + '</h6>';
        });

        bodyLines.forEach(function (body, i) {
            let colors = tooltipModel.labelColors[i];
            let style = 'background:' + colors.backgroundColor;
            style += '; border-color:' + colors.borderColor;
            let span = '<span class="square" style="' + style + '"></span>';
            innerHtml += span + body + '<br />';
        });

        return innerHtml;
    }

    return undefined;
}

export function defaultConfig(overide) {
    return _merge.merge({
        animation: false,
        responsive: true,
        maintainAspectRatio: false,
        layout: {
            padding: {
                top: 5
            }
        },
        tooltips: {
            mode: 'index',
            intersect: false,
            enabled: false,
        },
        scales: {
            xAxes: [{
                display: false,
            }],
            yAxes: [{
                display: false,
            }]
        },
        legend: {
            display: false,
        }
    }, overide);
}
