import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';
import '../services/api_service.dart';
import '../services/storage_service.dart';
import '../models/models.dart';
import 'main_screen.dart';

class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen>
    with TickerProviderStateMixin {
  late AnimationController _fadeController;
  late AnimationController _scaleController;
  late Animation<double> _fadeAnimation;
  late Animation<double> _scaleAnimation;

  String _statusText = 'Inicjalizacja aplikacji...';
  String _statusTextAwait = 'Przygotowywanie...';
  String _statusTextFinish = '';

  final ApiService _apiService = ApiService();
  final StorageService _storageService = StorageService.instance;

  @override
  void initState() {
    super.initState();
    _initAnimations();
    _startInitialization();
  }

  void _initAnimations() {
    _fadeController = AnimationController(
      duration: const Duration(milliseconds: 1500),
      vsync: this,
    );
    _scaleController = AnimationController(
      duration: const Duration(milliseconds: 1000),
      vsync: this,
    );

    _fadeAnimation = Tween<double>(
      begin: 0.0,
      end: 1.0,
    ).animate(CurvedAnimation(
      parent: _fadeController,
      curve: Curves.easeInOut,
    ));

    _scaleAnimation = Tween<double>(
      begin: 0.8,
      end: 1.0,
    ).animate(CurvedAnimation(
      parent: _scaleController,
      curve: Curves.elasticOut,
    ));

    _fadeController.forward();
    _scaleController.forward();
  }

  Future<void> _startInitialization() async {
    await Future.delayed(const Duration(milliseconds: 500));

    try {
      // Check and request permissions
      await _checkPermissions();

      // Load data
      await _loadData();

      // Navigate to main screen
      await _navigateToMain();
    } catch (error) {
      _showError('Błąd inicjalizacji: $error');
    }
  }

  Future<void> _checkPermissions() async {
    _updateStatus('Sprawdzanie uprawnień...', 'Weryfikacja...', '');

    // Check if permissions were already accepted
    if (_storageService.getPermissionsAccepted()) {
      return;
    }

    // Request required permissions
    final permissions = [
      Permission.notification,
      Permission.audio,
    ];

    Map<Permission, PermissionStatus> statuses = await permissions.request();

    bool allGranted = statuses.values.every(
      (status) => status == PermissionStatus.granted,
    );

    if (!allGranted) {
      // Show permission dialog
      await _showPermissionDialog();
    } else {
      await _storageService.setPermissionsAccepted(true);
    }
  }

  Future<void> _showPermissionDialog() async {
    final result = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (context) => AlertDialog(
        title: const Text('Uprawnienia'),
        content: const Text(
          'Aplikacja wymaga dostępu do powiadomień i audio aby poprawnie funkcjonować. '
          'Czy chcesz udzielić uprawnień?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Anuluj'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('Zezwól'),
          ),
        ],
      ),
    );

    if (result == true) {
      await _storageService.setPermissionsAccepted(true);
    } else {
      SystemNavigator.pop();
    }
  }

  Future<void> _loadData() async {
    _updateStatus('Ładowanie danych...', 'Pobieranie stacji radiowych...', '');

    try {
      // Try to load cached data first
      List<RadioStation>? stations = await _storageService.getCachedStations();
      List<Wojewodztwo>? okolica = await _storageService.getCachedOkolica();
      List<Swiatowe>? swiat = await _storageService.getCachedSwiat();
      List<RadioStation>? top10pop = await _storageService.getCachedTop10pop();

      // If cache is invalid or empty, fetch from API
      if (stations == null || stations.isEmpty) {
        _updateStatus('Pobieranie stacji...', 'Łączenie z serwerem...', '');
        stations = await _apiService.fetchStations();
        await _storageService.cacheStations(stations);
      }

      if (okolica == null || okolica.isEmpty) {
        _updateStatus('Pobieranie regionów...', 'Ładowanie województw...', '');
        okolica = await _apiService.fetchOkolice();
        await _storageService.cacheOkolica(okolica);
      }

      if (swiat == null || swiat.isEmpty) {
        _updateStatus('Pobieranie stacji światowych...', 'Ładowanie krajów...', '');
        swiat = await _apiService.fetchSwiat();
        await _storageService.cacheSwiat(swiat);
      }

      if (top10pop == null || top10pop.isEmpty) {
        _updateStatus('Pobieranie najpopularniejszych...', 'Finalizowanie...', '');
        top10pop = await _apiService.fetchTop10pop();
        await _storageService.cacheTop10pop(top10pop);
      }

    } catch (error) {
      // Try to use cached data even if it's old
      final cachedStations = await _storageService.getCachedStations();
      if (cachedStations != null && cachedStations.isNotEmpty) {
        _updateStatus('Używanie danych z pamięci...', 'Tryb offline...', '');
        await Future.delayed(const Duration(milliseconds: 1000));
      } else {
        throw Exception('Brak dostępu do danych. Sprawdź połączenie internetowe.');
      }
    }
  }

  Future<void> _navigateToMain() async {
    _updateStatus('Przygotowywanie interfejsu...', 'Prawie gotowe...', 'Zakończono!');
    
    await Future.delayed(const Duration(milliseconds: 1000));

    if (mounted) {
      Navigator.of(context).pushReplacement(
        MaterialPageRoute(
          builder: (context) => const MainScreen(),
        ),
      );
    }
  }

  void _updateStatus(String status, String await, String finish) {
    if (mounted) {
      setState(() {
        _statusText = status;
        _statusTextAwait = await;
        _statusTextFinish = finish;
      });
    }
  }

  void _showError(String message) {
    if (mounted) {
      showDialog(
        context: context,
        barrierDismissible: false,
        builder: (context) => AlertDialog(
          title: const Text('Błąd'),
          content: Text(message),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.of(context).pop();
                _startInitialization();
              },
              child: const Text('Spróbuj ponownie'),
            ),
            TextButton(
              onPressed: () => SystemNavigator.pop(),
              child: const Text('Wyjdź'),
            ),
          ],
        ),
      );
    }
  }

  @override
  void dispose() {
    _fadeController.dispose();
    _scaleController.dispose();
    _apiService.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Theme.of(context).colorScheme.primary,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            children: [
              Expanded(
                flex: 3,
                child: Center(
                  child: AnimatedBuilder(
                    animation: _scaleAnimation,
                    builder: (context, child) {
                      return Transform.scale(
                        scale: _scaleAnimation.value,
                        child: FadeTransition(
                          opacity: _fadeAnimation,
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              // App icon/logo
                              Container(
                                width: 120,
                                height: 120,
                                decoration: BoxDecoration(
                                  color: Colors.white,
                                  borderRadius: BorderRadius.circular(30),
                                  boxShadow: [
                                    BoxShadow(
                                      color: Colors.black.withOpacity(0.2),
                                      blurRadius: 20,
                                      offset: const Offset(0, 10),
                                    ),
                                  ],
                                ),
                                child: const Icon(
                                  Icons.radio,
                                  size: 60,
                                  color: Color(0xFF2196F3),
                                ),
                              ),
                              const SizedBox(height: 24),
                              // App name
                              Text(
                                'PulseFM',
                                style: Theme.of(context)
                                    .textTheme
                                    .headlineLarge
                                    ?.copyWith(
                                      color: Colors.white,
                                      fontWeight: FontWeight.bold,
                                    ),
                              ),
                              const SizedBox(height: 8),
                              Text(
                                'Radio w Twoich rękach',
                                style: Theme.of(context)
                                    .textTheme
                                    .titleMedium
                                    ?.copyWith(
                                      color: Colors.white70,
                                    ),
                              ),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
                ),
              ),
              
              // Status section
              Expanded(
                flex: 1,
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    // Loading indicator
                    const SizedBox(
                      width: 32,
                      height: 32,
                      child: CircularProgressIndicator(
                        color: Colors.white,
                        strokeWidth: 3,
                      ),
                    ),
                    const SizedBox(height: 24),
                    
                    // Status texts
                    Text(
                      _statusText,
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                            color: Colors.white,
                            fontWeight: FontWeight.w600,
                          ),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 8),
                    Text(
                      _statusTextAwait,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                            color: Colors.white70,
                          ),
                      textAlign: TextAlign.center,
                    ),
                    if (_statusTextFinish.isNotEmpty) ...[
                      const SizedBox(height: 8),
                      Text(
                        _statusTextFinish,
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                              color: Colors.white,
                              fontWeight: FontWeight.w500,
                            ),
                        textAlign: TextAlign.center,
                      ),
                    ],
                  ],
                ),
              ),
              
              // Footer
              Text(
                'Stackflow Studios',
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: Colors.white60,
                    ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}