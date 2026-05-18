using System.Globalization;

namespace ComponentVault.WinUI.Localization;

internal static class CategoryDisplay
{
    private static readonly IReadOnlyDictionary<string, string> ZhMap =
        new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            ["General"] = "通用",
            ["Capacitor"] = "电容",
            ["Resistor"] = "电阻",
            ["Inductor"] = "电感",
            ["Ferrite Bead"] = "磁珠",
            ["Diode"] = "二极管",
            ["LED"] = "发光二极管",
            ["Indicator"] = "指示器件",
            ["Transistor"] = "晶体管",
            ["MOSFET"] = "MOS 管",
            ["Regulator"] = "稳压器",
            ["Power"] = "电源",
            ["Battery"] = "电池",
            ["Fuse"] = "保险丝",
            ["Relay"] = "继电器",
            ["Switch"] = "开关",
            ["Button"] = "按键",
            ["Connector"] = "连接器",
            ["Wire-to-board"] = "线对板",
            ["Board-to-board"] = "板对板",
            ["Cable"] = "线缆",
            ["Crystal"] = "晶振",
            ["Oscillator"] = "振荡器",
            ["Sensor"] = "传感器",
            ["MCU"] = "单片机",
            ["Microcontroller"] = "单片机",
            ["IC"] = "集成电路",
            ["Memory"] = "存储器",
            ["Logic"] = "逻辑器件",
            ["OpAmp"] = "运放",
            ["Amplifier"] = "放大器",
            ["Driver"] = "驱动器",
            ["Display"] = "显示器件",
            ["Module"] = "模块",
            ["RF"] = "射频",
            ["Antenna"] = "天线",
            ["Protection"] = "保护器件",
        };

    internal static string Localize(string? category)
    {
        var normalized = category?.Trim();
        if (string.IsNullOrWhiteSpace(normalized))
        {
            return category ?? string.Empty;
        }

        if (!CultureInfo.CurrentUICulture.TwoLetterISOLanguageName.Equals("zh", StringComparison.OrdinalIgnoreCase))
        {
            return normalized;
        }

        return string.Join(
            " / ",
            normalized
                .Split('/', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
                .Select(segment => ZhMap.TryGetValue(segment, out var localized) ? localized : segment)
        );
    }
}
