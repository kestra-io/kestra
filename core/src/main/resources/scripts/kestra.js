function Kestra() {

}

Kestra._send = (map) => {
    console.log("::" + JSON.stringify(map) + "::");
}

Kestra._metrics = (name, type, value, tags) => {
    Kestra._send({
        "metrics": [
            {
                "name": name,
                "type": type,
                "value": value,
                tags: tags || {}
            }
        ]
    });
}

Kestra.outputs = (outputs) => {
    Kestra._send({
        "outputs": outputs
    });
}

Kestra.counter = (name, value, tags) => {
    Kestra._metrics(name, "counter", value, tags);
}

Kestra.timer = (name, duration, tags) => {
    if (typeof duration === "function") {
        const start = new Date();
        duration(() => {
            Kestra._metrics(name, "timer", (new Date() - start) / 1000, tags);
        });
    } else {
        Kestra._metrics(name, "timer", duration, tags)
    }
}

module.exports = Kestra;