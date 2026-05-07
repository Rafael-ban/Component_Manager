namespace ComponentVault.WinUI.Models;

public sealed class OperationResult
{
    public required bool IsSuccess { get; init; }
    public required string Message { get; init; }

    public static OperationResult Success(string message) =>
        new()
        {
            IsSuccess = true,
            Message = message,
        };

    public static OperationResult Failure(string message) =>
        new()
        {
            IsSuccess = false,
            Message = message,
        };
}
