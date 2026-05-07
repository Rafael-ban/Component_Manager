using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Windows.UI;
using ComponentVault.WinUI.Models;
using ComponentVault.WinUI.ViewModels;

namespace ComponentVault.WinUI.Views;

public sealed partial class MovementsView : Page
{
    private MainViewModel ViewModel => ((App)Application.Current).MainViewModel;

    public MovementsView()
    {
        InitializeComponent();
        DataContext = ViewModel;
    }

    private void OnRefreshFeedClicked(object sender, RoutedEventArgs e)
    {
        ViewModel.Refresh();
    }

    private async void OnRecordMovementClicked(object sender, RoutedEventArgs e)
    {
        if (ViewModel.IsBusy)
        {
            return;
        }

        if (ViewModel.AvailableComponents.Count == 0)
        {
            await ShowMessageAsync("Record movement", "Add a component before recording stock movement.");
            return;
        }

        var draft = await ShowMovementDialogAsync();
        if (draft is null)
        {
            return;
        }

        var result = ViewModel.RecordMovement(draft);
        await ShowMessageAsync(result.IsSuccess ? "Movement saved" : "Movement failed", result.Message);
    }

    private void OnMovementSelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        ViewModel.SelectedMovement = MovementsListView.SelectedItem as StockMovementRecord;
    }

    private async Task<MovementEntryDraft?> ShowMovementDialogAsync()
    {
        var componentCombo = new ComboBox
        {
            ItemsSource = ViewModel.AvailableComponents,
            DisplayMemberPath = nameof(ComponentRecord.Name),
            SelectedIndex = 0,
        };
        var movementTypeCombo = new ComboBox
        {
            ItemsSource = new[] { "inbound", "outbound", "adjustment" },
            SelectedIndex = 0,
        };
        var quantityBox = new NumberBox
        {
            Value = 1,
            Minimum = -100000,
            SmallChange = 1,
        };
        var reasonBox = new TextBox();
        var noteBox = new TextBox
        {
            AcceptsReturn = true,
            MinHeight = 70,
            TextWrapping = TextWrapping.Wrap,
        };
        var helperText = new TextBlock
        {
            Foreground = new SolidColorBrush(Color.FromArgb(255, 96, 96, 96)),
            Text = "Use positive values for inbound and outbound. Adjustment can be positive or negative.",
            TextWrapping = TextWrapping.Wrap,
        };
        var errorText = new TextBlock
        {
            Foreground = new SolidColorBrush(Color.FromArgb(255, 176, 0, 32)),
            TextWrapping = TextWrapping.Wrap,
        };

        var panel = new StackPanel { Spacing = 12 };
        panel.Children.Add(CreateField("Component", componentCombo));
        panel.Children.Add(CreateField("Movement type", movementTypeCombo));
        panel.Children.Add(CreateField("Quantity", quantityBox));
        panel.Children.Add(helperText);
        panel.Children.Add(CreateField("Reason", reasonBox));
        panel.Children.Add(CreateField("Note", noteBox));
        panel.Children.Add(errorText);

        MovementEntryDraft? draft = null;
        var dialog = new ContentDialog
        {
            Title = "Record stock movement",
            Content = panel,
            PrimaryButtonText = "Save",
            CloseButtonText = "Cancel",
            DefaultButton = ContentDialogButton.Primary,
            XamlRoot = XamlRoot,
        };
        dialog.PrimaryButtonClick += (_, args) =>
        {
            if (componentCombo.SelectedItem is not ComponentRecord component)
            {
                errorText.Text = "Choose a component.";
                args.Cancel = true;
                return;
            }

            if (movementTypeCombo.SelectedItem is not string movementType)
            {
                errorText.Text = "Choose a movement type.";
                args.Cancel = true;
                return;
            }

            if (double.IsNaN(quantityBox.Value))
            {
                errorText.Text = "Enter a valid quantity.";
                args.Cancel = true;
                return;
            }

            draft = new MovementEntryDraft
            {
                ComponentId = component.Id,
                MovementType = movementType,
                Quantity = (int)Math.Round(quantityBox.Value),
                Reason = reasonBox.Text,
                Note = noteBox.Text,
            };
        };

        var result = await dialog.ShowAsync();
        return result == ContentDialogResult.Primary ? draft : null;
    }

    private async Task ShowMessageAsync(string title, string message)
    {
        var dialog = new ContentDialog
        {
            Title = title,
            Content = message,
            CloseButtonText = "Close",
            XamlRoot = XamlRoot,
        };
        await dialog.ShowAsync();
    }

    private static FrameworkElement CreateField(string label, FrameworkElement control)
    {
        var panel = new StackPanel { Spacing = 6 };
        panel.Children.Add(new TextBlock { Text = label });
        panel.Children.Add(control);
        return panel;
    }
}
