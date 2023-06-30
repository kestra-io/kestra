@echo off

REM NOTE: Do not use backquotes in this bat file because backquotes are unintentionally recognized by sh.
REM NOTE: Just quotes are available for [ for /f "delims=" %%w ('...') ].

setlocal

REM Do not use %0 to identify the JAR (bat) file.
REM %0 is just "kestra" when run by just "> kestra" while %0 is "kestra.bat" when run by "> kestra.bat".
SET this=%~f0

REM Plugins path default to pwd & must be exported as env var
SET "current_dir=%~dp0"
IF NOT DEFINED kestra_plugins_path (set "kestra_plugins_path=%current_dir%plugins\")

REM Kestra configuration env vars

IF NOT DEFINED kestra_configuration_path (set "kestra_configuration_path=%current_dir%confs\")

IF DEFINED kestra_configuration (
    echo %kestra_configuration% > "%kestra_configuration_path%application.yml"
    set "micronaut_config_files=%kestra_configuration_path%application.yml"
)

REM Check java version
FOR /f "delims=" %%w in ('java -fullversion 2^>^&1') do set java_fullversion=%%w

ECHO %java_fullversion% | find " full version ""1." > NUL
IF NOT ERRORLEVEL 1 (set java_version=1)

ECHO %java_fullversion% | find " full version ""9" > NUL
IF NOT ERRORLEVEL 1 (set java_version=9)

ECHO %java_fullversion% | find " full version ""10" > NUL
IF NOT ERRORLEVEL 1 (set java_version=10)

IF NOT DEFINED java_version (set java_version=0)

IF %java_version% NEQ 0 (
    ECHO [ERROR] Kestra require at least Java 11.. 1>&2
    EXIT 1
)

REM Opens java.nio due to https://github.com/snowflakedb/snowflake-jdbc/issues/589
REM Opens java.util due to https://github.com/Azure/azure-sdk-for-java/issues/27806
SET JAVA_ADD_OPENS="--add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED"

java %JAVA_OPTS% %JAVA_ADD_OPENS% -jar "%this%" %*

ENDLOCAL

exit /b %ERRORLEVEL%
