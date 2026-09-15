using System.Collections.ObjectModel;
using ComponentVault.WinUI.Models;
using ComponentVault.WinUI.Services;
using ComponentVault.WinUI.Services.BatchInbound;
using ComponentVault.WinUI.Services.Catalog;
using ComponentVault.WinUI.ViewModels;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Windows.System;

namespace ComponentVault.WinUI.Views;

public sealed class BatchInboundEditableRow
{
    public required string ReceiptId{get;init;} public required string Raw{get;init;} public bool Selected{get;set;}=true;public string Sku{get;set;}="";public double QuantityInput{get;set;}=double.NaN;public string LocationId{get;set;}="";public string Name{get;set;}="";public string Category{get;set;}="";public string PackageName{get;set;}="";public string Description{get;set;}="";public string Reason{get;set;}="";public IReadOnlyList<string> LocationIds{get;init;}=[];
    public string Summary=>$"{Sku} · 包装 {ReceiptId[..8]}";
    public string ManualMetadata{get=>$"{Name} | {Category} | {PackageName}";set{var p=value.Split('|');Name=p.ElementAtOrDefault(0)?.Trim()??"";Category=p.ElementAtOrDefault(1)?.Trim()??"";PackageName=p.ElementAtOrDefault(2)?.Trim()??"";}}
    public int? Quantity=>double.IsFinite(QuantityInput)&&QuantityInput==Math.Truncate(QuantityInput)&&QuantityInput is >0 and <=int.MaxValue?(int)QuantityInput:null;
}

public sealed partial class BatchJlcInboundView:Page
{
    private readonly BatchInboundDraftStore _drafts=new();private readonly LcscPublicLookup _lookup=new();private readonly ObservableCollection<BatchInboundEditableRow> _ready=[];private readonly ObservableCollection<BatchInboundEditableRow> _pending=[];private readonly HashSet<string> _removed=[];private CancellationTokenSource? _lookupCancellation;private int _generation;private bool _draftLoadFailed;private bool _busy;
    private MainViewModel ViewModel=>((App)Application.Current).MainViewModel??throw new InvalidOperationException("MainViewModel unavailable");
    public BatchJlcInboundView(){InitializeComponent();ReadyList.ItemsSource=_ready;PendingList.ItemsSource=_pending;DefaultLocationBox.ItemsSource=ViewModel.StorageLocations;try{foreach(var item in _drafts.Load()){if(ViewModel.IsBatchReceiptCommitted(item.ReceiptId))continue;if(item.Status=="captured")continue;var row=FromDraft(item,ViewModel.StorageLocations.Select(x=>x.Id).ToArray());(item.Status=="ready"?_ready:_pending).Add(row);}RefreshCount();}catch(Exception ex){_draftLoadFailed=true;CountText.Text=ex.Message;}}
    private void OnBack(object sender,RoutedEventArgs e){if(_busy)return;if(((App)Application.Current).Window is MainWindow window)window.NavigateTo("Inventory");}
    private void OnCaptureKeyDown(object sender,KeyRoutedEventArgs e){if(!_busy&&e.Key==VirtualKey.Enter&&CaptureBox.Text.IndexOfAny(['\r','\n'])<0){Collect();e.Handled=true;}}
    private void OnCollect(object sender,RoutedEventArgs e){if(!_busy)Collect();}
    private void Collect(){try{var merged=_drafts.Merge(CaptureBox.Text.Split(['\r','\n'],StringSplitOptions.RemoveEmptyEntries));CaptureBox.Text="";CountText.Text=$"已收集 {merged.Items.Count}/500 个包装；本次忽略重复 {merged.Duplicates} 条。";}catch(Exception ex){CountText.Text=ex.Message;_=ShowBatchErrorAsync("草稿保存失败",ex.Message);}}
    private async void OnParse(object sender,RoutedEventArgs e){if(_busy)return;SetBusy(true);try{Collect();var shown=_ready.Concat(_pending).Select(x=>x.ReceiptId).ToHashSet();await ParseAsync(_drafts.Load().Where(x=>!shown.Contains(x.ReceiptId)&&!ViewModel.IsBatchReceiptCommitted(x.ReceiptId)));}catch(Exception ex){await ShowBatchErrorAsync("批量解析失败",ex.Message);}finally{SetBusy(false);}}
    private async Task ParseAsync(IEnumerable<BatchScanItem> scans)
    {
        _lookupCancellation?.Cancel();_lookupCancellation=new();var generation=++_generation;var token=_lookupCancellation.Token;var cache=new Dictionary<string,LcscProductMetadata?>(StringComparer.OrdinalIgnoreCase);var locations=ViewModel.StorageLocations.Select(x=>x.Id).ToArray();var defaultLocation=(DefaultLocationBox.SelectedItem as StorageLocationRecord)?.Id??locations.FirstOrDefault()??"";
        foreach(var scan in scans){if(token.IsCancellationRequested||generation!=_generation)break;Remove(scan.ReceiptId);var parsed=JlcQrParser.Parse(scan.Raw);var editedSku=LcscPublicCatalog.NormalizeSku(scan.Sku);var sku=editedSku??parsed.Sku;var skuChanged=editedSku is not null&&!editedSku.Equals(parsed.Sku,StringComparison.OrdinalIgnoreCase);var editedQuantity=int.TryParse(scan.QuantityText,out var manualQuantity)&&manualQuantity>0?manualQuantity:(int?)null;var row=new BatchInboundEditableRow{ReceiptId=scan.ReceiptId,Raw=scan.Raw,Selected=scan.Selected,Sku=sku,QuantityInput=editedQuantity??parsed.Quantity??double.NaN,LocationId=string.IsNullOrWhiteSpace(scan.LocationId)?defaultLocation:scan.LocationId,Name=skuChanged?"":scan.Name.Length>0?scan.Name:parsed.PartName??parsed.Model??"",Category=skuChanged?"":scan.Category,PackageName=skuChanged?"":scan.PackageName.Length>0?scan.PackageName:parsed.PackageDetail??"",Description=skuChanged?"":scan.Description,Reason=parsed.ParseError??scan.Error,LocationIds=locations};
            if(string.IsNullOrWhiteSpace(row.Sku)||row.Quantity is null){_pending.Add(row);continue;}var local=ViewModel.Components.FirstOrDefault(x=>!x.Deleted&&x.Sku.Equals(row.Sku,StringComparison.OrdinalIgnoreCase));if(local is not null){row.Name=local.Name;row.Category=local.Category;row.PackageName=local.PackageName;row.Description=local.Description;row.Reason="";_ready.Add(row);continue;}
            try{if(!cache.TryGetValue(row.Sku,out var metadata)){metadata=await _lookup.LookupAsync(row.Sku,token);cache[row.Sku]=metadata;}if(generation!=_generation)break;if(metadata is null){row.Reason="商城无精确匹配，请手动补齐资料。";_pending.Add(row);continue;}row.Name=metadata.Name;row.Category=metadata.Category;row.PackageName=metadata.PackageName??"";row.Description=CatalogNotes(metadata);if(Complete(row)){row.Reason="";_ready.Add(row);}else{row.Reason="商城资料不完整，请手动补齐名称、分类和封装。";_pending.Add(row);}}
            catch(OperationCanceledException){break;}catch(Exception ex){row.Reason=$"查询失败（{ex.GetType().Name}），可重试或手填。";_pending.Add(row);}
        }if(generation==_generation){SaveRemaining();RefreshCount();if(_pending.Count>0)await ShowBatchErrorAsync("部分包装需要处理",$"本批次有 {_pending.Count} 个包装未就绪，已保留在“待处理”页。可重试、手填或移除；就绪项可单独提交。");}
    }
    private static bool Complete(BatchInboundEditableRow x)=>x.Quantity is not null&&LcscPublicCatalog.NormalizeSku(x.Sku) is not null&&!string.IsNullOrWhiteSpace(x.LocationId)&&!string.IsNullOrWhiteSpace(x.Name)&&!string.IsNullOrWhiteSpace(x.Category)&&!string.IsNullOrWhiteSpace(x.PackageName);
    private static string CatalogNotes(LcscProductMetadata m){var lines=new List<string>();void Add(string? x){if(!string.IsNullOrWhiteSpace(x))lines.Add(x);}Add(m.Model is null?null:$"型号：{m.Model}");Add(m.Brand is null?null:$"品牌：{m.Brand}");Add($"官方商品页：{m.OfficialUrl}");Add(m.CategoryPath is null?null:$"官方分类路径：{m.CategoryPath}");Add(m.ImageUrl is null?null:$"商品图片：{m.ImageUrl}");Add(m.DatasheetUrl is null?null:$"数据手册：{m.DatasheetUrl}");if(m.Parameters is not null)foreach(var p in m.Parameters)Add($"参数·{p.Key}：{p.Value}");return string.Join("\n",lines);}
    private void Remove(string receipt){var ready=_ready.FirstOrDefault(x=>x.ReceiptId==receipt);if(ready is not null)_ready.Remove(ready);var pending=_pending.FirstOrDefault(x=>x.ReceiptId==receipt);if(pending is not null)_pending.Remove(pending);}
    private void OnCancelLookup(object sender,RoutedEventArgs e){_generation++;_lookupCancellation?.Cancel();CountText.Text="查询已取消；当前草稿和结果已保留。";}
    private void OnApplyDefaultLocation(object sender,RoutedEventArgs e){if(_busy)return;if(DefaultLocationBox.SelectedItem is StorageLocationRecord location)foreach(var row in _ready.Concat(_pending))row.LocationId=location.Id;SaveRemaining();ReadyList.ItemsSource=null;ReadyList.ItemsSource=_ready;PendingList.ItemsSource=null;PendingList.ItemsSource=_pending;}
    private async void OnRetryPending(object sender, RoutedEventArgs e)
    {
        if (_busy) return;
        SetBusy(true);
        try
        {
            var selected = PendingList.SelectedItems.Cast<BatchInboundEditableRow>().ToArray();
            var manualReady = selected.Where(row => Complete(row) &&
                row.Sku.Equals(JlcQrParser.Parse(row.Raw).Sku, StringComparison.OrdinalIgnoreCase)).ToArray();
            foreach (var row in manualReady)
            {
                row.Reason = "";
                _pending.Remove(row);
                _ready.Add(row);
            }
            SaveRemaining();
            await ParseAsync(selected.Except(manualReady).Select(ToDraft));
        }
        catch (Exception exception) { await ShowBatchErrorAsync("待处理重试失败", exception.Message); }
        finally { SetBusy(false); }
    }
    private void OnRemovePending(object sender,RoutedEventArgs e){if(_busy)return;foreach(var row in PendingList.SelectedItems.Cast<BatchInboundEditableRow>().ToArray()){_pending.Remove(row);_removed.Add(row.ReceiptId);}SaveRemaining();RefreshCount();}
    private void OnDraftFieldChanged(object sender,RoutedEventArgs e)=>PersistEdits();
    private void OnDraftFieldLostFocus(object sender,RoutedEventArgs e)=>PersistEdits();
    private void PersistEdits(){if(_busy||_draftLoadFailed)return;try{SaveRemaining();}catch(Exception ex){CountText.Text=ex.Message;_=ShowBatchErrorAsync("草稿保存失败",ex.Message);}}
    private async void OnSubmit(object sender,RoutedEventArgs e)
    {if(_busy)return;SetBusy(true);try{foreach(var invalid in _ready.Where(x=>!Complete(x)).ToArray()){invalid.Reason="数量、库位或资料已变为无效，请修正后重试。";_ready.Remove(invalid);_pending.Add(invalid);}var lines=_ready.Where(x=>x.Selected&&Complete(x)).Select(x=>new BatchInboundLine(x.ReceiptId,x.Raw,x.Sku,x.Quantity!.Value,x.LocationId,x.Name,x.Category,x.PackageName,x.Description)).ToArray();if(lines.Length==0){CountText.Text="没有可提交的 就绪包装。";await ShowBatchErrorAsync("无法提交",CountText.Text);SetBusy(false);return;}IReadOnlyList<BatchInboundSummary> summary;try{summary=InventoryStore.SummarizeBatchInbound(lines);}catch(OverflowException){CountText.Text="汇总数量超过整数范围。";await ShowBatchErrorAsync("无法提交",CountText.Text);SetBusy(false);return;}var dialog=new ContentDialog{Title="确认批量入库",Content=new TextBlock{Text=string.Join("\n",summary.Select(x=>$"{x.Sku} · {x.LocationId} · +{x.Quantity} · {x.Packages} 包")),TextWrapping=TextWrapping.Wrap},PrimaryButtonText="确认一次提交",CloseButtonText="取消",DefaultButton=ContentDialogButton.Close,XamlRoot=XamlRoot};if(await dialog.ShowAsync()!=ContentDialogResult.Primary){SetBusy(false);return;}var result=ViewModel.CommitBatchInbound(new(lines));if(result.IsSuccess){foreach(var row in _ready.Where(x=>lines.Any(y=>y.ReceiptId==x.ReceiptId)).ToArray())_ready.Remove(row);SaveRemaining();}else await ShowBatchErrorAsync("批量入库失败",result.Message);CountText.Text=result.Message;}catch(Exception ex){await ShowBatchErrorAsync("批量入库失败",ex.Message);}finally{SetBusy(false);}}
    private async void OnClearDraft(object sender,RoutedEventArgs e)
    {
        if (_busy) return;
        var dialog = new ContentDialog { Title="清空批量入库草稿？", Content="所有尚未提交的原始包装和人工修正将被删除。", PrimaryButtonText="确认清空", CloseButtonText="取消", DefaultButton=ContentDialogButton.Close, XamlRoot=XamlRoot };
        if (await dialog.ShowAsync()!=ContentDialogResult.Primary) return;
        _generation++;_lookupCancellation?.Cancel();_drafts.Clear();_draftLoadFailed=false;_removed.Clear();_ready.Clear();_pending.Clear();RefreshCount();
    }
    private void SaveRemaining(){var shown=_ready.Concat(_pending).Select(x=>x.ReceiptId).ToHashSet();var captured=_drafts.Load().Where(x=>!shown.Contains(x.ReceiptId)&&!_removed.Contains(x.ReceiptId));_drafts.Save(captured.Concat(_ready.Concat(_pending).Select(ToDraft)).ToArray());}
    private void RefreshCount()=>CountText.Text=$"草稿 {_drafts.Load().Count}/500 包 · 就绪 {_ready.Count} · 待处理 {_pending.Count}";
    private void OnUnloaded(object sender,RoutedEventArgs e){_generation++;_lookupCancellation?.Cancel();if(_draftLoadFailed)return;try{SaveRemaining();}catch(Exception ex){CountText.Text=ex.Message;}}
    private static BatchScanItem ToDraft(BatchInboundEditableRow x)=>new(x.ReceiptId,x.Raw){Status=x.Reason.Length==0&&Complete(x)?"ready":"pending",Selected=x.Selected,Sku=x.Sku,QuantityText=x.Quantity?.ToString()??"",LocationId=x.LocationId,Name=x.Name,Category=x.Category,PackageName=x.PackageName,Description=x.Description,Error=x.Reason};
    private static BatchInboundEditableRow FromDraft(BatchScanItem x,IReadOnlyList<string> locations)=>new(){ReceiptId=x.ReceiptId,Raw=x.Raw,Selected=x.Selected,Sku=x.Sku,QuantityInput=int.TryParse(x.QuantityText,out var q)?q:double.NaN,LocationId=x.LocationId,Name=x.Name,Category=x.Category,PackageName=x.PackageName,Description=x.Description,Reason=x.Error,LocationIds=locations};
    private void SetBusy(bool value){_busy=value;BackButton.IsEnabled=!value;CollectButton.IsEnabled=!value;ParseButton.IsEnabled=!value;ClearButton.IsEnabled=!value;ApplyLocationButton.IsEnabled=!value;SubmitButton.IsEnabled=!value;RetryButton.IsEnabled=!value;RemoveButton.IsEnabled=!value;CaptureBox.IsEnabled=!value;ReadyList.IsEnabled=!value;PendingList.IsEnabled=!value;DefaultLocationBox.IsEnabled=!value;}
    private async Task ShowBatchErrorAsync(string title,string message)=>await new ContentDialog{Title=title,Content=new TextBlock{Text=message,TextWrapping=TextWrapping.Wrap},CloseButtonText="关闭",XamlRoot=XamlRoot}.ShowAsync();
}
