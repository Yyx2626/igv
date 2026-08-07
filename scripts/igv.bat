setlocal
::Get the current batch file's short path
for %%x in (%0) do set BatchPath=%%~dpsx
for %%x in (%BatchPath%) do set BatchPath=%%~dpsx

set "WindowsArch=%PROCESSOR_ARCHITECTURE%"
if defined PROCESSOR_ARCHITEW6432 set "WindowsArch=%PROCESSOR_ARCHITEW6432%"
set "BundledJdk="
if /I "%WindowsArch%"=="ARM64" set "BundledJdk=jdk-21-windows-arm64"
if /I "%WindowsArch%"=="AMD64" set "BundledJdk=jdk-21-windows-x86_64"

set JAVA_CMD=java
if defined BundledJdk if exist %BatchPath%%BundledJdk%\bin\javaw.exe (
  echo "Using bundled %BundledJdk% JDK."
  set JAVA_HOME=%BatchPath%%BundledJdk%
  set JAVA_CMD=%BatchPath%%BundledJdk%\bin\javaw.exe
) else (
  echo "Using system JDK. IGV requires Java 21."
)

::-Xmx8g indicates 8 gb of memory.
::To adjust this (or other Java options), edit the "%USERPROFILE%\.igv\java_arguments" 
::file.  For more info, see the README at 
::https://raw.githubusercontent.com/igvteam/igv/master/scripts/readme.txt 

set CP=%BatchPath%lib\*

if exist "%USERPROFILE%\.igv\java_arguments" (
    %JAVA_CMD% -Xmx8g ^
        @%BatchPath%igv.args ^
        -Dsamjdk.snappy.disable=true ^
        -Djava.net.preferIPv4Stack=true ^
        -Djava.net.useSystemProxies=true ^
        @"%USERPROFILE%\.igv\java_arguments" ^
        -cp "%CP%" org.igv.ui.Main %*
) else (
    %JAVA_CMD% -Xmx8g ^
        @%BatchPath%igv.args ^
        -Dsamjdk.snappy.disable=true ^
        -Djava.net.preferIPv4Stack=true ^
        -Djava.net.useSystemProxies=true ^
        -cp "%CP%" org.igv.ui.Main %*
)
