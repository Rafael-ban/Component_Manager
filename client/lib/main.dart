import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'src/app_controller.dart';
import 'src/data/local_store.dart';
import 'src/data/settings_store.dart';
import 'src/services/sync_service.dart';
import 'src/ui/app_shell.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  LocalStore.configureDatabaseFactory();

  final localStore = LocalStore();
  final settingsStore = SettingsStore();
  final syncService = SyncService(
    localStore: localStore,
    settingsStore: settingsStore,
  );
  final controller = AppController(
    localStore: localStore,
    settingsStore: settingsStore,
    syncService: syncService,
  );

  await controller.initialize();

  runApp(
    ChangeNotifierProvider.value(
      value: controller,
      child: const ComponentVaultApp(),
    ),
  );
}

class ComponentVaultApp extends StatelessWidget {
  const ComponentVaultApp({super.key});

  @override
  Widget build(BuildContext context) {
    const seed = Color(0xFF0F766E);

    return MaterialApp(
      title: 'Component Vault',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: seed),
        useMaterial3: true,
        scaffoldBackgroundColor: const Color(0xFFF4F7F5),
        cardTheme: const CardThemeData(
          elevation: 0,
          margin: EdgeInsets.zero,
          color: Colors.white,
        ),
        appBarTheme: const AppBarTheme(
          centerTitle: false,
          backgroundColor: Color(0xFFF4F7F5),
          surfaceTintColor: Colors.transparent,
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: Colors.white,
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(16),
            borderSide: const BorderSide(color: Color(0xFFD3DDD8)),
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(16),
            borderSide: const BorderSide(color: Color(0xFFD3DDD8)),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(16),
            borderSide: const BorderSide(color: seed, width: 1.4),
          ),
        ),
      ),
      home: const InventoryAppShell(),
    );
  }
}

