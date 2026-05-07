# Windows Native Client

This directory contains the Windows-native desktop client built with WinUI 3.

## Current State

- NavigationView-based desktop shell implemented
- Dashboard, components, movements, and settings pages implemented
- Local SQLite persistence implemented
- Component create/edit/soft delete workflow implemented
- Movement entry workflow implemented
- Push/pull sync wiring implemented against the FastAPI service
- `dotnet build` verified successfully on `2026-05-07`
- MSIX-oriented `dotnet publish` verified successfully on `2026-05-07`

## Quick Check

```powershell
dotnet build windows-client\ComponentVault.WinUI\ComponentVault.WinUI.csproj
dotnet publish windows-client\ComponentVault.WinUI\ComponentVault.WinUI.csproj -c Release /p:PublishProfile=win-x64.pubxml
```

The publish command writes the Windows release package under:

- `windows-client\ComponentVault.WinUI\bin\Release\net9.0-windows10.0.19041.0\win-x64\AppPackages\`
