package io.kestra.webserver.controllers.api;

import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Hidden;

@Validated
@Controller("/api")
public class ApiController {
    @Value("${micronaut.server.context-path:}")
    protected String basePath;

    protected String getBasePath() {
        return basePath.replaceAll("/$", "");
    }

    protected String getSwaggerFilename() {
        return "kestra.yml";
    }

    @Get()
    @Hidden
    public HttpResponse<?> rapidoc() {
        String doc = "<!doctype html>\n" +
            "<html>\n" +
            "<head>\n" +
            "  <title>Api | Kestra</title>\n" +
            "  <meta charset='utf-8'/>\n" +
            "  <link rel=\"shortcut icon\" type=\"image/png\" href=\"/static/favicon.png\" />\n" +
            "  <meta name='viewport' content='width=device-width, minimum-scale=1, initial-scale=1, user-scalable=yes'/>\n" +
            "  <link href=\"https://fonts.googleapis.com/css2?family=Ubuntu:wght@300;400;700;900&display=swap\" rel=\"stylesheet\">\n" +
            "  <script src='https://unpkg.com/rapidoc/dist/rapidoc-min.js'></script>\n" +
            "</head>\n" +
            "<body>\n" +
            "  <rapi-doc " +
            "    id='rapidoc'\n" +
            "    layout=\"row\"\n" +
            "    sort-endpoints-by=\"path\"\n" +
            "    show-header=\"false\"\n" +
            "    theme=\"dark\"\n" +
            "    bg-color=\"#1b1e2a\"\n" +
            "    text-color=\"#c1c1d8\"\n" +
            "    primary-color=\"#4F83A5\"\n" +
            "    render-style=\"read\"\n" +
            "    schema-style=\"table\"\n" +
            "    regular-font='Ubuntu'\n" +
            "  >\n" +
            "    <img src=\"" + getBasePath() + "/static/logo.svg\" slot=\"nav-logo\" alt=\"logo\" />\n" +
            "\n" +
            "  </rapi-doc>\n" +
            "  <script>\n" +
            "      const rapidoc = document.getElementById('rapidoc');\n" +
            "      rapidoc.setAttribute('spec-url', '" + getBasePath() + "/swagger/" + getSwaggerFilename() + "');\n" +
            "  </script>\n" +
            "</body>\n" +
            "</html>\n";

        return HttpResponse
            .ok()
            .contentType(MediaType.TEXT_HTML_TYPE)
            .body(doc);
    }

}
