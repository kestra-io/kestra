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

# Fix required to use new DucksDB versions along side RocksDB
# https://github.com/kestra-io/plugin-jdbc/issues/165
LIBSTDC="/lib/x86_64-linux-gnu/libstdc++.so.6"
if [ "${LD_PRELOAD_ENABLED:-true}" = "true" ] && [ -z "$LD_PRELOAD" ] && [ -f "$LIBSTDC" ]; then
  export LD_PRELOAD="$LIBSTDC"
fi

# Java options that Kestra engineers think are best for Kestra, they should be added before JAVA_OPTS so they are overridable:
# -XX:MaxRAMPercentage=50.0: configure max heap to 50% of available RAM (default 25%)
KESTRA_JAVA_OPTS="-XX:MaxRAMPercentage=50.0"

# Exec
exec java ${KESTRA_JAVA_OPTS} ${JAVA_OPTS} ${JAVA_ADD_OPENS} -Djava.security.manager=allow -jar "$0" "$@"
exit 127