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
    [a-z]*\ full\ version\ \"\(1|9|10\)\..*\")
        echo "[ERROR] Kestra require at least Java 11." 1>&2
        exit 1
        ;;
esac

# Exec
exec java ${JAVA_OPTS} -jar "$0" "$@"
exit 127