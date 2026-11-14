; Установщик для Windows (Inno Setup 6). Ставит в папку пользователя без прав администратора,
; новая версия ставится поверх старой, старый установщик .msi удаляется сам.
; Сборка: сначала ./gradlew :desktop:createDistributable -PappVersion=X.Y.Z, затем
;   iscc /DAppVersion=X.Y.Z packaging\windows\ebis.iss      → dist\windows\EbisOnoKonem-X.Y.Z-windows-x64-setup.exe

#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif
#ifndef SourceDir
  #define SourceDir AddBackslash(SourcePath) + "..\..\desktop\build\compose\binaries\main\app\EbisOnoKonem"
#endif
#ifndef OutputDir
  #define OutputDir AddBackslash(SourcePath) + "..\..\dist\windows"
#endif
#define AppName "Ебись оно конём"
#define AppExe "EbisOnoKonem.exe"
; Тот же AppUserModelID, что ставит само приложение: иначе Windows не покажет имя и значок в уведомлениях.
#define AppUserModelID "Konem.EbisOnoKonem"

[Setup]
AppId={{6F6B6F6E-656D-4562-6973-494E4E4F5345}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher=plusultra
AppPublisherURL=https://github.com/pluusultra/ebis-ono-konem
AppSupportURL=https://github.com/pluusultra/ebis-ono-konem/issues
AppUpdatesURL=https://github.com/pluusultra/ebis-ono-konem/releases
VersionInfoVersion={#AppVersion}
VersionInfoProductName={#AppName}
VersionInfoDescription={#AppName} — установщик
DefaultDirName={localappdata}\Programs\EbisOnoKonem
DisableDirPage=auto
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
OutputDir={#OutputDir}
OutputBaseFilename=EbisOnoKonem-{#AppVersion}-windows-x64-setup
SetupIconFile=..\..\desktop\icons\icon.ico
UninstallDisplayIcon={app}\{#AppExe}
UninstallDisplayName={#AppName}
WizardStyle=modern
WizardImageFile=wizard-100.bmp,wizard-150.bmp,wizard-200.bmp
WizardSmallImageFile=wizard-small-100.bmp,wizard-small-150.bmp,wizard-small-200.bmp
Compression=lzma2/ultra64
SolidCompression=yes
CloseApplications=force
RestartApplications=no
ShowLanguageDialog=auto

[Languages]
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Messages]
russian.WelcomeLabel1=Ну здарова, бедолага
russian.WelcomeLabel2=Сейчас на компьютер приедет [name/ver] — трекер всей херни, которую надо сделать.%n%nОн будет ныть, напоминать и стыдить, пока не сделаешь. Задачи синхронизируются с телефоном через любую общую папку.%n%nЗакрой приложение, если оно открыто, и жми «Далее».
russian.FinishedHeadingLabel=Конь приехал
russian.FinishedLabel=[name] установлен. Ярлык — в меню «Пуск».%n%nТеперь отступать некуда.
russian.ClickFinish=Жми «Завершить» и иди работать.
english.WelcomeLabel1=Welcome, poor soul
english.WelcomeLabel2=This will install [name/ver], a task tracker that nags you until stuff gets done.%n%nClose the app if it is running, then click Next.
english.FinishedHeadingLabel=The horse has arrived

[CustomMessages]
russian.DesktopIcon=Ярлык на рабочем столе
russian.LaunchApp=Запустить и начать страдать
english.DesktopIcon=Desktop shortcut
english.LaunchApp=Launch now

[Tasks]
Name: "desktopicon"; Description: "{cm:DesktopIcon}"; Flags: unchecked

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[InstallDelete]
; Старые файлы прошлой версии (другие jar с версией в имени) — чтобы не копились.
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"

[Icons]
Name: "{userprograms}\{#AppName}"; Filename: "{app}\{#AppExe}"; AppUserModelID: "{#AppUserModelID}"
Name: "{userdesktop}\{#AppName}"; Filename: "{app}\{#AppExe}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExe}"; Description: "{cm:LaunchApp}"; Flags: nowait postinstall skipifsilent

[Code]
const
  UninstKey = 'Software\Microsoft\Windows\CurrentVersion\Uninstall';

procedure KillApp();
var
  Code: Integer;
begin
  Exec(ExpandConstant('{sys}\taskkill.exe'), '/F /T /IM {#AppExe}', '', SW_HIDE, ewWaitUntilTerminated, Code);
end;

{ Раньше раздавался .msi (jpackage). Если он стоит — тихо удаляем, чтобы не было двух копий. Задачи и настройки не трогаются. }
procedure RemoveOldMsi();
var
  Keys: TArrayOfString;
  I, Code: Integer;
  Name: String;
begin
  if not RegGetSubkeyNames(HKCU, UninstKey, Keys) then Exit;
  for I := 0 to GetArrayLength(Keys) - 1 do
    if (Copy(Keys[I], 1, 1) = '{') and RegQueryStringValue(HKCU, UninstKey + '\' + Keys[I], 'DisplayName', Name)
       and (Name = 'EbisOnoKonem') then
      Exec(ExpandConstant('{sys}\msiexec.exe'), '/x ' + Keys[I] + ' /qn /norestart', '', SW_HIDE, ewWaitUntilTerminated, Code);
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  KillApp();
  RemoveOldMsi();
  Result := '';
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usUninstall then
    KillApp();
  if CurUninstallStep = usPostUninstall then
  begin
    { То, что приложение прописывает само: автозапуск, ссылка ebis-konem:// для кнопок уведомлений, ярлык в «Пуске». }
    RegDeleteValue(HKCU, 'Software\Microsoft\Windows\CurrentVersion\Run', 'EbisOnoKonem');
    RegDeleteKeyIncludingSubkeys(HKCU, 'Software\Classes\ebis-konem');
    DeleteFile(ExpandConstant('{userprograms}\{#AppName}.lnk'));
  end;
end;
