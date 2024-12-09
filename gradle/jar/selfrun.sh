# Plugins path default to pwd & must be exported as env var
KESTRA_PLUGINS_PATH=${KESTRA_PLUGINS_PATH:-"$(dirname "$0")/plugins"}
export KESTRA_PLUGINS_PATH=${KESTRA_PLUGINS_PATH}

# Kestra configuration env vars
KESTRA_CONFIGURATION_PATH=${KESTRA_CONFIGURATION_PATH:-"$(dirname "$0")/confs"}
if [ "${KESTRA_CONFIGURATION}" ]; then
    echo "${KESTRA_CONFIGURATION}" > "${KESTRA_CONFIGURATION_PATH}/application.yml"
    export MICRONAUT_CONFIG_FILES="${KESTRA_CONFIGURATION_PATH}/application.yml"
fi

# Check java version
JAVA_FULLVERSION=$(java -fullversion 2>&1)
case "$JAVA_FULLVERSION" in
    [a-z]*\ full\ version\ \"\(1|9|10|11|12|13|14|15|16|17|18|19|20\)\..*\")
        echo "[ERROR] Kestra require at least Java 21." 1>&2
        exit 1
        ;;
esac

# Opens java.nio due to https://github.com/snowflakedb/snowflake-jdbc/issues/589
# Opens java.util due to https://github.com/Azure/azure-sdk-for-java/issues/27806
# Opens java.lang due to https://github.com/kestra-io/kestra/issues/1755, see https://github.com/micronaut-projects/micronaut-core/issues/9573
JAVA_ADD_OPENS="--add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED"

# classpath for Kestra
CLASSPATH="$CLASSPATH":"$0"

# classpath for additional libs
if [ -d "$(dirname "$0")/libs" ] ; then
CLASSPATH="$CLASSPATH:$(dirname "$0")/libs/*"
fi

# Remove a possible colon prefix from the classpath
CLASSPATH=${CLASSPATH#:}

if [ -z "$KESTRA_RUN_CLASS" ]; then
    KESTRA_RUN_CLASS=io.kestra.cli.App
fi

# Exec
exec java ${JAVA_OPTS} -cp "${CLASSPATH}" ${JAVA_ADD_OPENS} -Djava.security.manager=allow ${KESTRA_RUN_CLASS} "$@"
exit 127