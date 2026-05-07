# Windows Native Client

This directory contains the Windows-native desktop client built with WinUI 3.

## Current State

- NavigationView-based desktop shell implemented
- Dashboard, components, movements, and settings pages implemented
- Local SQLite persistence implemented
- Component create/edit/soft delete workflow implemented
- Movement entry workflow implemented
- Push/pull sync wiring implemented against the FastAPI service
- Chinese-first WinUI page copy and desktop-oriented detail panels implemented
- XAML designer sample data added for dashboard, components, movements, and
  settings pages so the main screens can be previewed directly in Visual Studio
- `dotnet build` verified successfully on `2026-05-07`
- MSIX-oriented `dotnet publish` verified successfully on `2026-05-07`
- portable unpackaged `dotnet publish` verified successfully on `2026-05-08`

## Quick Check

```powershell
dotnet build windows-client\ComponentVault.WinUI\ComponentVault.WinUI.csproj
dotnet publish windows-client\ComponentVault.WinUI\ComponentVault.WinUI.csproj -c Release /p:PublishProfile=win-x64.pubxml
dotnet publish windows-client\ComponentVault.WinUI\ComponentVault.WinUI.csproj -c Release -r win-x64 -p:PublishProfile= -p:WindowsPackageType=None -p:GenerateAppxPackageOnBuild=false -p:AppxPackageSigningEnabled=false -p:WindowsAppSDKSelfContained=true -p:SelfContained=true -p:PublishSingleFile=false -o windows-client\artifacts\portable-local
```

The publish commands write the Windows release outputs under:

- `windows-client\ComponentVault.WinUI\bin\Release\net9.0-windows10.0.19041.0\win-x64\AppPackages\`
- `windows-client\artifacts\portable-local\`

Use the MSIX output when you want a packaged Windows install flow. Use the
portable output when you want a zip that can be extracted and run directly via
`ComponentVault.WinUI.exe`.

If startup fails on a target machine, the app now writes a diagnostic log to:

- `%LOCALAPPDATA%\ComponentVault\logs\startup.log`

The runtime also shows a native Windows error dialog with the log path.

## Visual Editing

Use Visual Studio 2022 with the WinUI 3 workload installed.

1. Open `windows-client\ComponentVault.WinUI\ComponentVault.WinUI.csproj`.
2. Open one of the page files under `ComponentVault.WinUI\Views\`:
   `DashboardView.xaml`, `ComponentsView.xaml`, `MovementsView.xaml`, or
   `SettingsView.xaml`.
3. Open the XAML Designer or split view.
4. Use Hot Reload while the app is running for runtime refinement.

Designer support is backed by:

- `ComponentVault.WinUI\Design\DesignMainViewModel.cs`
- `ComponentVault.WinUI\Design\ViewModelResolver.cs`

These files provide design-time sample data and prevent the page constructors
from depending on the live application state while the designer is loading.
