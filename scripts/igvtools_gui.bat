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

if exist "%USERPROFILE%\.igv\java_arguments" (
  start %JAVA_CMD% -showversion --module-path=%BatchPath%\lib -Xmx1500m @%BatchPath%\igv.args @"%USERPROFILE%\.igv\java_arguments" --module=org.igv/org.igv.tools.IgvTools gui
) else (
  start %JAVA_CMD% -showversion --module-path=%BatchPath%\lib -Xmx1500m @%BatchPath%\igv.args --module=org.igv/org.igv.tools.IgvTools gui
)
