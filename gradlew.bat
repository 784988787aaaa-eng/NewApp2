@rem ============================================================================
@rem  Gradle startup script for Windows - SmartLedger Pro Android
@rem ============================================================================

@echo off
setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

where gradle >nul 2>&1
if "%ERRORLEVEL%" == "0" (
    gradle %*
    goto end
)

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
if exist "%CLASSPATH%" (
    java.exe -Dorg.gradle.appname=%APP_BASE_NAME% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
    goto end
)

echo ERROR: Could not find gradle command in PATH or gradle-wrapper.jar.
exit /b 1

:end
exit /b 0
