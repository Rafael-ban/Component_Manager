using ComponentVault.WinUI.Design;
using Microsoft.UI.Xaml.Controls;

namespace ComponentVault.WinUI.Views;

public sealed partial class DashboardView : Page
{
    public DashboardView()
    {
        InitializeComponent();
        DataContext = ViewModelResolver.ResolveMainViewModel();
    }
}
