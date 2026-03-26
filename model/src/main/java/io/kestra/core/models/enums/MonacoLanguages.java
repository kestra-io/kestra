package io.kestra.core.models.enums;

public enum MonacoLanguages {
    NONE(""),
    ABAP("abap"),
    APEX("apex"),
    AZCLI("azcli"),
    BAT("bat"),
    BICEP("bicep"),
    CAMELIGO("cameligo"),
    CLOJURE("clojure"),
    COFFEE("coffee"),
    CPP("cpp"),
    CSHARP("csharp"),
    CSP("csp"),
    CSS("css"),
    CYPHER("cypher"),
    DART("dart"),
    DOCKERFILE("dockerfile"),
    ECL("ecl"),
    ELIXIR("elixir"),
    FLOW9("flow9"),
    FREEMARKER2("freemarker2"),
    FSHARP("fsharp"),
    GO("go"),
    GRAPHQL("graphql"),
    HANDLEBARS("handlebars"),
    HCL("hcl"),
    HTML("html"),
    INI("ini"),
    JAVA("java"),
    JAVASCRIPT("javascript"),
    JULIA("julia"),
    KOTLIN("kotlin"),
    LESS("less"),
    LEXON("lexon"),
    LIQUID("liquid"),
    LUA("lua"),
    M3("m3"),
    MARKDOWN("markdown"),
    MDX("mdx"),
    MIPS("mips"),
    MSDAX("msdax"),
    MYSQL("mysql"),
    OBJECTIVE_C("objective-c"),
    PASCAL("pascal"),
    PASCALIGO("pascaligo"),
    PERL("perl"),
    PGSQL("pgsql"),
    PHP("php"),
    PLA("pla"),
    POSTIATS("postiats"),
    POWERQUERY("powerquery"),
    POWERSHELL("powershell"),
    PROTOBUF("protobuf"),
    PUG("pug"),
    PYTHON("python"),
    QSHARP("qsharp"),
    R("r"),
    JAVASCRIPT_REACT("razor"),
    REDIS("redis"),
    REDSHIFT("redshift"),
    RESTRUCTUREDTEXT("restructuredtext"),
    RUBY("ruby"),
    RUST("rust"),
    SB("sb"),
    SCALA("scala"),
    SCHEME("scheme"),
    SCSS("scss"),
    SHELL("shell"),
    SOLIDITY("solidity"),
    SOPHIA("sophia"),
    SPARQL("sparql"),
    SQL("sql"),
    ST("st"),
    SWIFT("swift"),
    SYSTEMVERILOG("systemverilog"),
    TCL("tcl"),
    TEST("test"),
    TWIG("twig"),
    TYPESCRIPT("typescript"),
    TYPESPEC("typespec"),
    VB("vb"),
    WGSL("wgsl"),
    XML("xml"),
    YAML("yaml");

    private final String value;

    MonacoLanguages(String value) {
        this.value = value;
    }

    public String value() {
        return this.value;
    }

    @Override
    public String toString() {
        return this.value;
    }
}
