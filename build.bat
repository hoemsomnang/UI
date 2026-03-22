@echo off
cd /d "%~dp0"

set "JFX_JMODS=C:\javafx-jmods-25"
set "APP_ICON=%~dp0dist\icons\logo_512.ico"

jpackage ^
  --type exe ^
  --name "LemonTool" ^
  --app-version 1.0.0 ^
  --vendor "Lemon Tool" ^
  --input "%~dp0dist" ^
  --main-jar LemonTool.jar ^
  --main-class application.Main ^
  --module-path "%JFX_JMODS%" ^
  --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.media,javafx.web,java.logging,java.sql,jdk.charsets ^
  --java-options "--enable-native-access=javafx.graphics" ^
  --icon "%APP_ICON%" ^
  --win-dir-chooser ^
  --win-shortcut ^
  --win-menu ^
  --win-menu-group "Lemon Tool" ^
  --win-per-user-install ^
  --dest "%~dp0output" ^
  --verbose

pause