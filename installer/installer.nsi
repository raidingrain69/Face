; ===========================================================
;  AI Face Unlocker – NSIS Installer Script
;  Build with: makensis installer.nsi
;
;  Prerequisites:
;    - NSIS 3.x  (https://nsis.sourceforge.io)
;    - The fat JAR already built:
;        target\face-unlocker-1.0.0-jar-with-dependencies.jar
;    - The .exe launcher already created by Launch4j:
;        target\FaceUnlocker.exe
;    - A bundled JRE in:  jre\
;        (copy from https://adoptium.net or use jlink)
; ===========================================================

Unicode True

; Product metadata
!define PRODUCT_NAME        "AI Face Unlocker"
!define PRODUCT_VERSION     "1.0.0"
!define PRODUCT_PUBLISHER   "Your Company"
!define PRODUCT_URL         "https://github.com/your-org/face-unlocker"
!define PRODUCT_EXE         "FaceUnlocker.exe"
!define INSTALL_DIR         "$PROGRAMFILES64\${PRODUCT_NAME}"
!define REG_UNINSTALL        "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_NAME}"
!define REG_STARTUP          "Software\Microsoft\Windows\CurrentVersion\Run"

Name "${PRODUCT_NAME} ${PRODUCT_VERSION}"
OutFile "FaceUnlocker-Setup-${PRODUCT_VERSION}.exe"
InstallDir "${INSTALL_DIR}"
InstallDirRegKey HKLM "${REG_UNINSTALL}" "InstallLocation"
RequestExecutionLevel admin
SetCompressor /SOLID lzma

; ---- Pages ----------------------------------------------------------------
!include "MUI2.nsh"
!define MUI_ABORTWARNING
!define MUI_ICON   "..\src\main\resources\icons\app-icon.ico"
!define MUI_UNICON "..\src\main\resources\icons\app-icon.ico"

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "..\LICENSE"
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "English"

; ---- Installer section ----------------------------------------------------
Section "Install" SecInstall

  SetOutPath "$INSTDIR"

  ; Main executable (created by Launch4j wrapping the fat JAR)
  File "..\target\FaceUnlocker.exe"

  ; Fat JAR (Launch4j launcher references this)
  File "..\target\face-unlocker-1.0.0-jar-with-dependencies.jar"

  ; Bundled JRE
  SetOutPath "$INSTDIR\jre"
  File /r "..\jre\*.*"

  ; Write uninstaller
  WriteUninstaller "$INSTDIR\Uninstall.exe"

  ; Registry – Add/Remove Programs entry
  WriteRegStr   HKLM "${REG_UNINSTALL}" "DisplayName"       "${PRODUCT_NAME}"
  WriteRegStr   HKLM "${REG_UNINSTALL}" "DisplayVersion"    "${PRODUCT_VERSION}"
  WriteRegStr   HKLM "${REG_UNINSTALL}" "Publisher"         "${PRODUCT_PUBLISHER}"
  WriteRegStr   HKLM "${REG_UNINSTALL}" "URLInfoAbout"      "${PRODUCT_URL}"
  WriteRegStr   HKLM "${REG_UNINSTALL}" "InstallLocation"   "$INSTDIR"
  WriteRegStr   HKLM "${REG_UNINSTALL}" "UninstallString"   "$INSTDIR\Uninstall.exe"
  WriteRegDWORD HKLM "${REG_UNINSTALL}" "NoModify"          1
  WriteRegDWORD HKLM "${REG_UNINSTALL}" "NoRepair"          1

  ; Start Menu shortcut
  CreateDirectory "$SMPROGRAMS\${PRODUCT_NAME}"
  CreateShortcut  "$SMPROGRAMS\${PRODUCT_NAME}\${PRODUCT_NAME}.lnk" \
                  "$INSTDIR\${PRODUCT_EXE}"
  CreateShortcut  "$SMPROGRAMS\${PRODUCT_NAME}\Uninstall.lnk" \
                  "$INSTDIR\Uninstall.exe"

  ; Desktop shortcut
  CreateShortcut "$DESKTOP\${PRODUCT_NAME}.lnk" "$INSTDIR\${PRODUCT_EXE}"

  ; Auto-start registry key (optional – user can disable in dashboard)
  WriteRegStr HKCU "${REG_STARTUP}" "${PRODUCT_NAME}" \
              '"$INSTDIR\${PRODUCT_EXE}" --minimized'

SectionEnd

; ---- Uninstaller section --------------------------------------------------
Section "Uninstall"

  ; Remove from startup
  DeleteRegValue HKCU "${REG_STARTUP}" "${PRODUCT_NAME}"

  ; Remove files
  Delete "$INSTDIR\${PRODUCT_EXE}"
  Delete "$INSTDIR\face-unlocker-1.0.0-jar-with-dependencies.jar"
  Delete "$INSTDIR\Uninstall.exe"
  RMDir  /r "$INSTDIR\jre"
  RMDir  "$INSTDIR"

  ; Remove shortcuts
  Delete "$SMPROGRAMS\${PRODUCT_NAME}\*.lnk"
  RMDir  "$SMPROGRAMS\${PRODUCT_NAME}"
  Delete "$DESKTOP\${PRODUCT_NAME}.lnk"

  ; Remove registry entries
  DeleteRegKey HKLM "${REG_UNINSTALL}"

SectionEnd
