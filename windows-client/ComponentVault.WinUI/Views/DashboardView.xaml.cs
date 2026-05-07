using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace ComponentVault.WinUI.Views;

public sealed partial class DashboardView : Page
{
    public DashboardView()
    {
        InitializeComponent();
        DataContext = ((App)Application.Current).MainViewModel;
    }
}
