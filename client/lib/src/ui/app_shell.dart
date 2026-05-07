import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:uuid/uuid.dart';

import '../app_controller.dart';
import '../models.dart';

class InventoryAppShell extends StatefulWidget {
  const InventoryAppShell({super.key});

  @override
  State<InventoryAppShell> createState() => _InventoryAppShellState();
}

class _InventoryAppShellState extends State<InventoryAppShell> {
  int _selectedIndex = 0;

  @override
  Widget build(BuildContext context) {
    return Consumer<AppController>(
      builder: (context, controller, child) {
        if (!controller.isReady) {
          return const Scaffold(
            body: Center(child: CircularProgressIndicator()),
          );
        }

        final wideLayout = MediaQuery.sizeOf(context).width >= 980;
        final currentPage = _buildPage(controller);

        return Scaffold(
          appBar: AppBar(
            title: const Text('Component Vault'),
            actions: [
              IconButton(
                tooltip: 'Refresh',
                onPressed: controller.isRefreshing
                    ? null
                    : () => controller.refresh(),
                icon: const Icon(Icons.refresh_rounded),
              ),
              IconButton(
                tooltip: 'Sync now',
                onPressed: controller.isSyncing
                    ? null
                    : () => _runAction(
                        context,
                        () async {
                          await controller.syncNow();
                          return controller.syncConfiguration.lastSyncMessage;
                        },
                      ),
                icon: controller.isSyncing
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.sync_rounded),
              ),
              const SizedBox(width: 8),
            ],
          ),
          body: wideLayout
              ? Row(
                  children: [
                    NavigationRail(
                      selectedIndex: _selectedIndex,
                      onDestinationSelected: (index) {
                        setState(() {
                          _selectedIndex = index;
                        });
                      },
                      labelType: NavigationRailLabelType.all,
                      destinations: const [
                        NavigationRailDestination(
                          icon: Icon(Icons.space_dashboard_outlined),
                          selectedIcon: Icon(Icons.space_dashboard),
                          label: Text('Dashboard'),
                        ),
                        NavigationRailDestination(
                          icon: Icon(Icons.memory_outlined),
                          selectedIcon: Icon(Icons.memory),
                          label: Text('Components'),
                        ),
                        NavigationRailDestination(
                          icon: Icon(Icons.inventory_2_outlined),
                          selectedIcon: Icon(Icons.inventory_2),
                          label: Text('Movements'),
                        ),
                        NavigationRailDestination(
                          icon: Icon(Icons.settings_outlined),
                          selectedIcon: Icon(Icons.settings),
                          label: Text('Settings'),
                        ),
                      ],
                    ),
                    const VerticalDivider(width: 1),
                    Expanded(child: currentPage),
                  ],
                )
              : currentPage,
          bottomNavigationBar: wideLayout
              ? null
              : NavigationBar(
                  selectedIndex: _selectedIndex,
                  onDestinationSelected: (index) {
                    setState(() {
                      _selectedIndex = index;
                    });
                  },
                  destinations: const [
                    NavigationDestination(
                      icon: Icon(Icons.space_dashboard_outlined),
                      selectedIcon: Icon(Icons.space_dashboard),
                      label: 'Dashboard',
                    ),
                    NavigationDestination(
                      icon: Icon(Icons.memory_outlined),
                      selectedIcon: Icon(Icons.memory),
                      label: 'Components',
                    ),
                    NavigationDestination(
                      icon: Icon(Icons.inventory_2_outlined),
                      selectedIcon: Icon(Icons.inventory_2),
                      label: 'Movements',
                    ),
                    NavigationDestination(
                      icon: Icon(Icons.settings_outlined),
                      selectedIcon: Icon(Icons.settings),
                      label: 'Settings',
                    ),
                  ],
                ),
          floatingActionButton: _buildFab(context, controller),
        );
      },
    );
  }

  Widget _buildPage(AppController controller) {
    switch (_selectedIndex) {
      case 0:
        return DashboardPage(controller: controller);
      case 1:
        return ComponentsPage(controller: controller);
      case 2:
        return MovementsPage(controller: controller);
      case 3:
        return SettingsPage(
          controller: controller,
          configuration: controller.syncConfiguration,
        );
      default:
        return const SizedBox.shrink();
    }
  }

  Widget? _buildFab(BuildContext context, AppController controller) {
    if (_selectedIndex == 1) {
      return FloatingActionButton.extended(
        onPressed: () => _showComponentDialog(context, controller),
        icon: const Icon(Icons.add),
        label: const Text('Add component'),
      );
    }

    if (_selectedIndex == 2) {
      return FloatingActionButton.extended(
        onPressed: controller.components.isEmpty
            ? null
            : () => _showMovementDialog(context, controller),
        icon: const Icon(Icons.playlist_add_rounded),
        label: const Text('Add movement'),
      );
    }

    return null;
  }
}

class DashboardPage extends StatelessWidget {
  const DashboardPage({super.key, required this.controller});

  final AppController controller;

  @override
  Widget build(BuildContext context) {
    final lowStockItems = controller.components
        .where((item) => item.isLowStock)
        .take(8)
        .toList();
    final recentMovements = controller.stockMovements.take(5).toList();

    return RefreshIndicator(
      onRefresh: controller.refresh,
      child: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          Wrap(
            spacing: 16,
            runSpacing: 16,
            children: [
              _MetricCard(
                title: 'Components',
                value: controller.dashboard.componentCount.toString(),
                icon: Icons.memory_rounded,
              ),
              _MetricCard(
                title: 'Units on hand',
                value: controller.dashboard.totalUnits.toString(),
                icon: Icons.inventory_2_rounded,
              ),
              _MetricCard(
                title: 'Low stock',
                value: controller.dashboard.lowStockCount.toString(),
                icon: Icons.warning_amber_rounded,
              ),
            ],
          ),
          const SizedBox(height: 24),
          _SectionCard(
            title: 'Low stock watchlist',
            subtitle: 'Components at or below the minimum threshold.',
            child: lowStockItems.isEmpty
                ? const _EmptyState(
                    icon: Icons.check_circle_outline_rounded,
                    title: 'No low-stock components',
                    message: 'All tracked components are above their threshold.',
                  )
                : Column(
                    children: lowStockItems
                        .map(
                          (component) => ListTile(
                            contentPadding: EdgeInsets.zero,
                            title: Text(component.name),
                            subtitle: Text(
                              '${component.sku}  |  ${component.location}',
                            ),
                            trailing: Chip(
                              label: Text(
                                '${component.quantity}/${component.minStock}',
                              ),
                              backgroundColor: const Color(0xFFFDE8E8),
                            ),
                          ),
                        )
                        .toList(),
                  ),
          ),
          const SizedBox(height: 16),
          _SectionCard(
            title: 'Recent activity',
            subtitle: 'Latest stock changes stored on this device.',
            child: recentMovements.isEmpty
                ? const _EmptyState(
                    icon: Icons.history_toggle_off_rounded,
                    title: 'No stock movements yet',
                    message: 'Use the movement screen to start tracking stock.',
                  )
                : Column(
                    children: recentMovements
                        .map(
                          (movement) => ListTile(
                            contentPadding: EdgeInsets.zero,
                            title: Text(
                              movement.componentName ?? movement.componentId,
                            ),
                            subtitle: Text(
                              '${movement.movementType.label}  |  ${movement.reason}',
                            ),
                            trailing: Text(
                              movement.quantity.toString(),
                              style: Theme.of(context).textTheme.titleMedium,
                            ),
                          ),
                        )
                        .toList(),
                  ),
          ),
        ],
      ),
    );
  }
}

class ComponentsPage extends StatelessWidget {
  const ComponentsPage({super.key, required this.controller});

  final AppController controller;

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: controller.refresh,
      child: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          _PageHeader(
            title: 'Components',
            subtitle: 'Track each SKU, package, location, and stock level.',
            action: FilledButton.icon(
              onPressed: () => _showComponentDialog(context, controller),
              icon: const Icon(Icons.add),
              label: const Text('Add component'),
            ),
          ),
          const SizedBox(height: 16),
          if (controller.components.isEmpty)
            const _EmptyState(
              icon: Icons.memory_outlined,
              title: 'No components yet',
              message: 'Create the first component record to start tracking stock.',
            )
          else
            ...controller.components.map(
              (component) => Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Card(
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(24),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.all(20),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    component.name,
                                    style: Theme.of(context)
                                        .textTheme
                                        .titleLarge,
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    '${component.sku}  |  ${component.category}  |  ${component.packageName}',
                                    style: Theme.of(context)
                                        .textTheme
                                        .bodyMedium
                                        ?.copyWith(color: Colors.black54),
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(width: 12),
                            Chip(
                              avatar: const Icon(Icons.inventory_2_outlined),
                              label: Text('${component.quantity} units'),
                              backgroundColor: component.isLowStock
                                  ? const Color(0xFFFDE8E8)
                                  : const Color(0xFFE8F5F3),
                            ),
                          ],
                        ),
                        const SizedBox(height: 16),
                        Wrap(
                          spacing: 12,
                          runSpacing: 12,
                          children: [
                            _DetailPill(
                              icon: Icons.place_outlined,
                              label: component.location,
                            ),
                            _DetailPill(
                              icon: Icons.warning_amber_rounded,
                              label: 'Min ${component.minStock}',
                            ),
                          ],
                        ),
                        if (component.description.isNotEmpty) ...[
                          const SizedBox(height: 12),
                          Text(
                            component.description,
                            style: Theme.of(context).textTheme.bodyMedium,
                          ),
                        ],
                        const SizedBox(height: 16),
                        Wrap(
                          spacing: 8,
                          runSpacing: 8,
                          children: [
                            TextButton.icon(
                              onPressed: () => _showComponentDialog(
                                context,
                                controller,
                                existing: component,
                              ),
                              icon: const Icon(Icons.edit_outlined),
                              label: const Text('Edit'),
                            ),
                            TextButton.icon(
                              onPressed: () => _confirmDeleteComponent(
                                context,
                                controller,
                                component,
                              ),
                              icon: const Icon(Icons.delete_outline_rounded),
                              label: const Text('Delete'),
                            ),
                            OutlinedButton.icon(
                              onPressed: () => _showMovementDialog(
                                context,
                                controller,
                                preselectedComponent: component,
                              ),
                              icon: const Icon(Icons.add_chart_rounded),
                              label: const Text('Record movement'),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class MovementsPage extends StatelessWidget {
  const MovementsPage({super.key, required this.controller});

  final AppController controller;

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: controller.refresh,
      child: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          _PageHeader(
            title: 'Stock movements',
            subtitle: 'Every inbound, outbound, and adjustment is stored locally.',
            action: FilledButton.icon(
              onPressed: controller.components.isEmpty
                  ? null
                  : () => _showMovementDialog(context, controller),
              icon: const Icon(Icons.playlist_add),
              label: const Text('Add movement'),
            ),
          ),
          const SizedBox(height: 16),
          if (controller.stockMovements.isEmpty)
            const _EmptyState(
              icon: Icons.inventory_2_outlined,
              title: 'No movements recorded',
              message: 'Record inbound or outbound activity to build history.',
            )
          else
            ...controller.stockMovements.map(
              (movement) => Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Card(
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(24),
                  ),
                  child: ListTile(
                    contentPadding: const EdgeInsets.all(20),
                    title: Text(movement.componentName ?? movement.componentId),
                    subtitle: Text(
                      '${movement.movementType.label}  |  ${movement.reason}\n${_formatDateTime(movement.happenedAt)}',
                    ),
                    isThreeLine: true,
                    trailing: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        Chip(
                          label: Text(
                            movement.quantity > 0
                                ? '+${movement.quantity}'
                                : movement.quantity.toString(),
                          ),
                        ),
                        if ((movement.componentSku ?? '').isNotEmpty)
                          Text(
                            movement.componentSku!,
                            style: Theme.of(context)
                                .textTheme
                                .bodySmall
                                ?.copyWith(color: Colors.black54),
                          ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class SettingsPage extends StatefulWidget {
  const SettingsPage({
    super.key,
    required this.controller,
    required this.configuration,
  });

  final AppController controller;
  final SyncConfiguration configuration;

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  late final TextEditingController _serverBaseUrlController;
  late final TextEditingController _apiTokenController;
  late bool _autoSyncEnabled;

  @override
  void initState() {
    super.initState();
    _serverBaseUrlController = TextEditingController(
      text: widget.configuration.serverBaseUrl,
    );
    _apiTokenController = TextEditingController(
      text: widget.configuration.apiToken,
    );
    _autoSyncEnabled = widget.configuration.autoSyncEnabled;
  }

  @override
  void didUpdateWidget(covariant SettingsPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (_serverBaseUrlController.text == oldWidget.configuration.serverBaseUrl) {
      _serverBaseUrlController.text = widget.configuration.serverBaseUrl;
    }
    if (_apiTokenController.text == oldWidget.configuration.apiToken) {
      _apiTokenController.text = widget.configuration.apiToken;
    }
    if (_autoSyncEnabled == oldWidget.configuration.autoSyncEnabled) {
      _autoSyncEnabled = widget.configuration.autoSyncEnabled;
    }
  }

  @override
  void dispose() {
    _serverBaseUrlController.dispose();
    _apiTokenController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final configuration = widget.configuration;

    return ListView(
      padding: const EdgeInsets.all(24),
      children: [
        const _PageHeader(
          title: 'Settings',
          subtitle: 'Configure the self-hosted sync endpoint and local sync behavior.',
        ),
        const SizedBox(height: 16),
        _SectionCard(
          title: 'Sync endpoint',
          subtitle: 'The app always writes locally first. Cloud sync is optional.',
          child: Column(
            children: [
              TextField(
                controller: _serverBaseUrlController,
                decoration: const InputDecoration(
                  labelText: 'Server base URL',
                  hintText: 'https://example.com:8787',
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _apiTokenController,
                decoration: const InputDecoration(
                  labelText: 'API token',
                ),
                obscureText: true,
              ),
              const SizedBox(height: 12),
              SwitchListTile.adaptive(
                contentPadding: EdgeInsets.zero,
                value: _autoSyncEnabled,
                title: const Text('Enable auto sync'),
                subtitle: const Text(
                  'Auto sync runs at startup and once per minute while the app is open.',
                ),
                onChanged: (value) {
                  setState(() {
                    _autoSyncEnabled = value;
                  });
                },
              ),
              const SizedBox(height: 12),
              Wrap(
                spacing: 12,
                runSpacing: 12,
                children: [
                  FilledButton.icon(
                    onPressed: () => _saveSettings(context),
                    icon: const Icon(Icons.save_outlined),
                    label: const Text('Save settings'),
                  ),
                  OutlinedButton.icon(
                    onPressed: configuration.isConfigured
                        ? () => _runAction(context, widget.controller.testConnection)
                        : null,
                    icon: const Icon(Icons.wifi_tethering_rounded),
                    label: const Text('Test connection'),
                  ),
                  OutlinedButton.icon(
                    onPressed: configuration.isConfigured
                        ? () => _runAction(
                              context,
                              () async {
                                await widget.controller.syncNow();
                                return widget
                                    .controller
                                    .syncConfiguration
                                    .lastSyncMessage;
                              },
                            )
                        : null,
                    icon: const Icon(Icons.sync_rounded),
                    label: const Text('Sync now'),
                  ),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        _SectionCard(
          title: 'Device status',
          subtitle: 'Useful for identifying this client during sync.',
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _StatusRow(label: 'Device ID', value: configuration.deviceId),
              _StatusRow(
                label: 'Last synced at',
                value: configuration.lastSyncedAt == null
                    ? 'Never'
                    : _formatDateTime(configuration.lastSyncedAt!),
              ),
              _StatusRow(
                label: 'Last sync result',
                value: configuration.lastSyncMessage,
              ),
            ],
          ),
        ),
      ],
    );
  }

  Future<void> _saveSettings(BuildContext context) async {
    final current = widget.configuration;
    final endpointChanged =
        current.serverBaseUrl.trim() != _serverBaseUrlController.text.trim() ||
        current.apiToken.trim() != _apiTokenController.text.trim();
    final updated = current.copyWith(
      serverBaseUrl: _serverBaseUrlController.text,
      apiToken: _apiTokenController.text,
      autoSyncEnabled: _autoSyncEnabled,
      clearLastSyncedAt: endpointChanged,
      lastSyncMessage:
          endpointChanged ? 'Endpoint changed. Sync pending.' : current.lastSyncMessage,
    );

    await widget.controller.saveSyncConfiguration(updated);
    if (!context.mounted) {
      return;
    }
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Settings saved')),
    );
  }
}

class _PageHeader extends StatelessWidget {
  const _PageHeader({
    required this.title,
    required this.subtitle,
    this.action,
  });

  final String title;
  final String subtitle;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final titleBlock = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: Theme.of(context).textTheme.headlineSmall,
        ),
        const SizedBox(height: 6),
        Text(
          subtitle,
          style: Theme.of(context)
              .textTheme
              .bodyMedium
              ?.copyWith(color: Colors.black54),
        ),
      ],
    );

    return LayoutBuilder(
      builder: (context, constraints) {
        final stackVertically = action != null && constraints.maxWidth < 720;
        if (stackVertically) {
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              titleBlock,
              const SizedBox(height: 16),
              action!,
            ],
          );
        }

        return Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(child: titleBlock),
            if (action != null) ...[
              const SizedBox(width: 16),
              action!,
            ],
          ],
        );
      },
    );
  }
}

class _MetricCard extends StatelessWidget {
  const _MetricCard({
    required this.title,
    required this.value,
    required this.icon,
  });

  final String title;
  final String value;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 240,
      child: Card(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Row(
            children: [
              CircleAvatar(
                radius: 24,
                backgroundColor: const Color(0xFFE8F5F3),
                child: Icon(icon, color: Theme.of(context).colorScheme.primary),
              ),
              const SizedBox(width: 16),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: Theme.of(context)
                        .textTheme
                        .bodyMedium
                        ?.copyWith(color: Colors.black54),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    value,
                    style: Theme.of(context).textTheme.headlineSmall,
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _SectionCard extends StatelessWidget {
  const _SectionCard({
    required this.title,
    required this.subtitle,
    required this.child,
  });

  final String title;
  final String subtitle;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Card(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 6),
            Text(
              subtitle,
              style: Theme.of(context)
                  .textTheme
                  .bodyMedium
                  ?.copyWith(color: Colors.black54),
            ),
            const SizedBox(height: 16),
            child,
          ],
        ),
      ),
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState({
    required this.icon,
    required this.title,
    required this.message,
  });

  final IconData icon;
  final String title;
  final String message;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 24),
      child: Column(
        children: [
          Icon(icon, size: 40, color: Colors.black45),
          const SizedBox(height: 12),
          Text(title, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 6),
          Text(
            message,
            textAlign: TextAlign.center,
            style: Theme.of(context)
                .textTheme
                .bodyMedium
                ?.copyWith(color: Colors.black54),
          ),
        ],
      ),
    );
  }
}

class _DetailPill extends StatelessWidget {
  const _DetailPill({
    required this.icon,
    required this.label,
  });

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(16),
        color: const Color(0xFFF3F5F4),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 18),
          const SizedBox(width: 8),
          Text(label),
        ],
      ),
    );
  }
}

class _StatusRow extends StatelessWidget {
  const _StatusRow({
    required this.label,
    required this.value,
  });

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 120,
            child: Text(
              label,
              style: Theme.of(context)
                  .textTheme
                  .bodyMedium
                  ?.copyWith(color: Colors.black54),
            ),
          ),
          Expanded(child: Text(value)),
        ],
      ),
    );
  }
}

Future<void> _showComponentDialog(
  BuildContext context,
  AppController controller, {
  ComponentRecord? existing,
}) async {
  final formKey = GlobalKey<FormState>();
  final skuController = TextEditingController(text: existing?.sku ?? '');
  final nameController = TextEditingController(text: existing?.name ?? '');
  final categoryController = TextEditingController(
    text: existing?.category ?? '',
  );
  final packageController = TextEditingController(
    text: existing?.packageName ?? '',
  );
  final locationController = TextEditingController(
    text: existing?.location ?? '',
  );
  final quantityController = TextEditingController(
    text: (existing?.quantity ?? 0).toString(),
  );
  final minStockController = TextEditingController(
    text: (existing?.minStock ?? 0).toString(),
  );
  final descriptionController = TextEditingController(
    text: existing?.description ?? '',
  );

  try {
    await showDialog<void>(
      context: context,
      builder: (dialogContext) {
        var isSaving = false;
        return StatefulBuilder(
          builder: (dialogContext, setState) {
            return AlertDialog(
              title: Text(existing == null ? 'Add component' : 'Edit component'),
              content: SizedBox(
                width: 520,
                child: Form(
                  key: formKey,
                  child: SingleChildScrollView(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        TextFormField(
                          controller: skuController,
                          decoration: const InputDecoration(labelText: 'SKU'),
                          validator: _requiredValidator,
                        ),
                        const SizedBox(height: 12),
                        TextFormField(
                          controller: nameController,
                          decoration: const InputDecoration(labelText: 'Name'),
                          validator: _requiredValidator,
                        ),
                        const SizedBox(height: 12),
                        TextFormField(
                          controller: categoryController,
                          decoration: const InputDecoration(labelText: 'Category'),
                          validator: _requiredValidator,
                        ),
                        const SizedBox(height: 12),
                        TextFormField(
                          controller: packageController,
                          decoration: const InputDecoration(labelText: 'Package'),
                          validator: _requiredValidator,
                        ),
                        const SizedBox(height: 12),
                        TextFormField(
                          controller: locationController,
                          decoration: const InputDecoration(labelText: 'Location'),
                          validator: _requiredValidator,
                        ),
                        const SizedBox(height: 12),
                        Row(
                          children: [
                            Expanded(
                              child: TextFormField(
                                controller: quantityController,
                                keyboardType: TextInputType.number,
                                decoration: const InputDecoration(
                                  labelText: 'Quantity',
                                ),
                                validator: _nonNegativeIntegerValidator,
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: TextFormField(
                                controller: minStockController,
                                keyboardType: TextInputType.number,
                                decoration: const InputDecoration(
                                  labelText: 'Minimum stock',
                                ),
                                validator: _nonNegativeIntegerValidator,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        TextFormField(
                          controller: descriptionController,
                          minLines: 2,
                          maxLines: 4,
                          decoration: const InputDecoration(
                            labelText: 'Description',
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
              actions: [
                TextButton(
                  onPressed: isSaving
                      ? null
                      : () => Navigator.of(dialogContext).pop(),
                  child: const Text('Cancel'),
                ),
                FilledButton(
                  onPressed: isSaving
                      ? null
                      : () async {
                          if (!formKey.currentState!.validate()) {
                            return;
                          }
                          setState(() {
                            isSaving = true;
                          });

                          try {
                            final now = DateTime.now().toUtc();
                            final record = ComponentRecord(
                              id: existing?.id ?? const Uuid().v4(),
                              sku: skuController.text.trim(),
                              name: nameController.text.trim(),
                              category: categoryController.text.trim(),
                              packageName: packageController.text.trim(),
                              location: locationController.text.trim(),
                              description: descriptionController.text.trim(),
                              quantity: int.parse(quantityController.text.trim()),
                              minStock: int.parse(minStockController.text.trim()),
                              updatedAt: now,
                              deleted: false,
                            );
                            await controller.saveComponent(record);
                            if (!dialogContext.mounted) {
                              return;
                            }
                            Navigator.of(dialogContext).pop();
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(
                                content: Text(
                                  existing == null
                                      ? 'Component created'
                                      : 'Component updated',
                                ),
                              ),
                            );
                          } catch (error) {
                            if (!context.mounted) {
                              return;
                            }
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(content: Text(error.toString())),
                            );
                          } finally {
                            if (dialogContext.mounted) {
                              setState(() {
                                isSaving = false;
                              });
                            }
                          }
                        },
                  child: const Text('Save'),
                ),
              ],
            );
          },
        );
      },
    );
  } finally {
    skuController.dispose();
    nameController.dispose();
    categoryController.dispose();
    packageController.dispose();
    locationController.dispose();
    quantityController.dispose();
    minStockController.dispose();
    descriptionController.dispose();
  }
}

Future<void> _showMovementDialog(
  BuildContext context,
  AppController controller, {
  ComponentRecord? preselectedComponent,
}) async {
  if (controller.components.isEmpty) {
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Add a component before recording movement.')),
    );
    return;
  }

  final formKey = GlobalKey<FormState>();
  var selectedComponent = preselectedComponent ?? controller.components.first;
  var movementType = MovementType.inbound;
  final quantityController = TextEditingController(text: '1');
  final reasonController = TextEditingController();
  final noteController = TextEditingController();

  try {
    await showDialog<void>(
      context: context,
      builder: (dialogContext) {
        var isSaving = false;
        return StatefulBuilder(
          builder: (dialogContext, setState) {
            return AlertDialog(
              title: const Text('Record movement'),
              content: SizedBox(
                width: 520,
                child: Form(
                  key: formKey,
                  child: SingleChildScrollView(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        DropdownButtonFormField<String>(
                          value: selectedComponent.id,
                          decoration: const InputDecoration(
                            labelText: 'Component',
                          ),
                          items: controller.components
                              .map(
                                (component) => DropdownMenuItem(
                                  value: component.id,
                                  child: Text(
                                    '${component.sku}  |  ${component.name}',
                                  ),
                                ),
                              )
                              .toList(),
                          onChanged: (value) {
                            if (value == null) {
                              return;
                            }
                            setState(() {
                              selectedComponent =
                                  controller.components.firstWhere(
                                (component) => component.id == value,
                              );
                            });
                          },
                        ),
                        const SizedBox(height: 12),
                        DropdownButtonFormField<MovementType>(
                          value: movementType,
                          decoration: const InputDecoration(
                            labelText: 'Movement type',
                          ),
                          items: MovementType.values
                              .map(
                                (type) => DropdownMenuItem(
                                  value: type,
                                  child: Text(type.label),
                                ),
                              )
                              .toList(),
                          onChanged: (value) {
                            if (value == null) {
                              return;
                            }
                            setState(() {
                              movementType = value;
                            });
                          },
                        ),
                        const SizedBox(height: 12),
                        TextFormField(
                          controller: quantityController,
                          keyboardType: TextInputType.number,
                          decoration: InputDecoration(
                            labelText: movementType == MovementType.adjustment
                                ? 'Adjustment delta'
                                : 'Quantity',
                            helperText: movementType == MovementType.adjustment
                                ? 'Use a negative value to reduce stock.'
                                : 'Use a positive value.',
                          ),
                          validator: (value) {
                            final parsed = int.tryParse(value?.trim() ?? '');
                            if (parsed == null) {
                              return 'Enter a valid integer.';
                            }
                            if (movementType != MovementType.adjustment &&
                                parsed <= 0) {
                              return 'Use a positive quantity.';
                            }
                            if (movementType == MovementType.adjustment &&
                                parsed == 0) {
                              return 'Adjustment cannot be zero.';
                            }
                            return null;
                          },
                        ),
                        const SizedBox(height: 12),
                        TextFormField(
                          controller: reasonController,
                          decoration: const InputDecoration(labelText: 'Reason'),
                          validator: _requiredValidator,
                        ),
                        const SizedBox(height: 12),
                        TextFormField(
                          controller: noteController,
                          minLines: 2,
                          maxLines: 4,
                          decoration: const InputDecoration(labelText: 'Note'),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
              actions: [
                TextButton(
                  onPressed: isSaving
                      ? null
                      : () => Navigator.of(dialogContext).pop(),
                  child: const Text('Cancel'),
                ),
                FilledButton(
                  onPressed: isSaving
                      ? null
                      : () async {
                          if (!formKey.currentState!.validate()) {
                            return;
                          }
                          setState(() {
                            isSaving = true;
                          });

                          try {
                            final now = DateTime.now().toUtc();
                            final movement = StockMovementRecord(
                              id: const Uuid().v4(),
                              componentId: selectedComponent.id,
                              movementType: movementType,
                              quantity: int.parse(quantityController.text.trim()),
                              reason: reasonController.text.trim(),
                              note: noteController.text.trim(),
                              happenedAt: now,
                              updatedAt: now,
                              deleted: false,
                              componentName: selectedComponent.name,
                              componentSku: selectedComponent.sku,
                            );
                            await controller.saveStockMovement(movement);
                            if (!dialogContext.mounted) {
                              return;
                            }
                            Navigator.of(dialogContext).pop();
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(
                                content: Text('Movement recorded'),
                              ),
                            );
                          } catch (error) {
                            if (!context.mounted) {
                              return;
                            }
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(content: Text(error.toString())),
                            );
                          } finally {
                            if (dialogContext.mounted) {
                              setState(() {
                                isSaving = false;
                              });
                            }
                          }
                        },
                  child: const Text('Save'),
                ),
              ],
            );
          },
        );
      },
    );
  } finally {
    quantityController.dispose();
    reasonController.dispose();
    noteController.dispose();
  }
}

Future<void> _confirmDeleteComponent(
  BuildContext context,
  AppController controller,
  ComponentRecord component,
) async {
  final shouldDelete = await showDialog<bool>(
    context: context,
    builder: (dialogContext) {
      return AlertDialog(
        title: const Text('Delete component'),
        content: Text(
          'Delete ${component.name}? The record will be soft deleted and can still sync to the server.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Delete'),
          ),
        ],
      );
    },
  );

  if (shouldDelete != true || !context.mounted) {
    return;
  }

  await _runAction(
    context,
    () => controller.removeComponent(component.id),
    successMessage: 'Component deleted',
  );
}

Future<void> _runAction(
  BuildContext context,
  Future<Object?> Function() action, {
  String? successMessage,
}) async {
  try {
    final result = await action();
    final resolvedMessage = successMessage ?? (result is String ? result : null);
    if (!context.mounted ||
        resolvedMessage == null ||
        resolvedMessage.isEmpty) {
      return;
    }
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(resolvedMessage)),
    );
  } catch (error) {
    if (!context.mounted) {
      return;
    }
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(error.toString())),
    );
  }
}

String? _requiredValidator(String? value) {
  if (value == null || value.trim().isEmpty) {
    return 'This field is required.';
  }
  return null;
}

String? _integerValidator(String? value) {
  if (value == null || value.trim().isEmpty) {
    return 'Enter a number.';
  }
  if (int.tryParse(value.trim()) == null) {
    return 'Enter a valid integer.';
  }
  return null;
}

String? _nonNegativeIntegerValidator(String? value) {
  final integerError = _integerValidator(value);
  if (integerError != null) {
    return integerError;
  }
  if (int.parse(value!.trim()) < 0) {
    return 'Use zero or a positive integer.';
  }
  return null;
}

String _formatDateTime(DateTime value) {
  final local = value.toLocal();
  final month = local.month.toString().padLeft(2, '0');
  final day = local.day.toString().padLeft(2, '0');
  final hour = local.hour.toString().padLeft(2, '0');
  final minute = local.minute.toString().padLeft(2, '0');
  return '${local.year}-$month-$day $hour:$minute';
}
