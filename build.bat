@echo off
set "JFX_JMODS=C:\javafx-jmods-25"

jpackage ^
  --type exe ^
  --name "InstagramDownloader" ^
  --input dist ^
  --main-jar InstagramDownloader.jar ^
  --main-class application.Main ^
  --module-path "%JFX_JMODS%" ^
  --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.media,javafx.web,java.logging,java.sql,jdk.charsets ^
  --java-options "--enable-native-access=javafx.graphics" ^
  --win-dir-chooser ^
  --win-shortcut ^
  --win-menu ^
  --win-console ^
  --dest output ^
  --verbose

pause