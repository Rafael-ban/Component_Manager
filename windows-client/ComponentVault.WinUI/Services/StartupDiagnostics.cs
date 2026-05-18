using System.Runtime.InteropServices;
using System.Text;
using ComponentVault.WinUI.Localization;

namespace ComponentVault.WinUI.Services;

internal static class StartupDiagnostics
{
    private const uint OkIconError = 0x00000010;

    public static string LogException(string area, Exception exception)
    {
        var logDirectory = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "ComponentVault",
            "logs"
        );
        Directory.CreateDirectory(logDirectory);

        var logPath = Path.Combine(logDirectory, "startup.log");
        var builder = new StringBuilder();
        builder.AppendLine($"[{DateTimeOffset.UtcNow:O}] {area}");
        builder.AppendLine(exception.ToString());
        builder.AppendLine();
        File.AppendAllText(logPath, builder.ToString(), Encoding.UTF8);
        return logPath;
    }

    public static void ShowStartupFailure(string title, Exception exception, string logPath)
    {
        ShowMessageBox(
            title,
            $"{exception.Message}{Environment.NewLine}{Environment.NewLine}{AppStrings.Format("Windows_Diagnostics_LogPathPattern", logPath)}"
        );
    }

    public static void ShowRuntimeFailure(string title, Exception exception, string logPath)
    {
        ShowMessageBox(
            title,
            $"{exception.Message}{Environment.NewLine}{Environment.NewLine}{AppStrings.Format("Windows_Diagnostics_LogPathPattern", logPath)}"
        );
    }

    private static void ShowMessageBox(string title, string message)
    {
        MessageBox(IntPtr.Zero, message, title, OkIconError);
    }

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int MessageBox(
        IntPtr hWnd,
        string text,
        string caption,
        uint type
    );
}
