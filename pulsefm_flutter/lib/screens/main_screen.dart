import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../bloc/radio_player/radio_player_bloc.dart';
import '../bloc/radio_player/radio_player_state.dart';
import '../bloc/radio_player/radio_player_event.dart';
import '../models/models.dart';
import '../services/storage_service.dart';
import '../widgets/player_widget.dart';

class MainScreen extends StatefulWidget {
  const MainScreen({super.key});

  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> {
  int _currentIndex = 0;
  final StorageService _storageService = StorageService.instance;

  final List<RadioStation> _sampleStations = const [
    RadioStation(
      name: 'RMF FM',
      url: 'https://rs9-krk2.rmfstream.pl/RMFFM48',
      city: 'Kraków',
      icon: 'https://example.com/rmf_logo.png',
    ),
    RadioStation(
      name: 'Radio ZET',
      url: 'https://radiozetmp3-01.eurozet.pl:8400/radiozetmp3',
      city: 'Warszawa',
      icon: 'https://example.com/zet_logo.png',
    ),
    RadioStation(
      name: 'Radio Maryja',
      url: 'https://radiomaryja.fastcast4u.com/proxy/radiomaryja?mp=/1',
      city: 'Toruń',
      icon: 'https://example.com/maryja_logo.png',
    ),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('PulseFM'),
        actions: [
          IconButton(
            icon: Icon(
              _storageService.getNightMode() 
                  ? Icons.light_mode 
                  : Icons.dark_mode,
            ),
            onPressed: _toggleTheme,
          ),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: _buildBody(),
          ),
          // Player widget at bottom
          BlocBuilder<RadioPlayerBloc, RadioPlayerState>(
            builder: (context, state) {
              if (state.hasCurrentStation) {
                return const PlayerWidget();
              }
              return const SizedBox.shrink();
            },
          ),
        ],
      ),
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _currentIndex,
        onTap: (index) {
          setState(() {
            _currentIndex = index;
          });
        },
        type: BottomNavigationBarType.fixed,
        items: const [
          BottomNavigationBarItem(
            icon: Icon(Icons.home),
            label: 'Główna',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.location_on),
            label: 'Regiony',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.public),
            label: 'Świat',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.favorite),
            label: 'Ulubione',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.more_horiz),
            label: 'Więcej',
          ),
        ],
      ),
    );
  }

  Widget _buildBody() {
    switch (_currentIndex) {
      case 0:
        return _buildHomeTab();
      case 1:
        return _buildRegionsTab();
      case 2:
        return _buildWorldTab();
      case 3:
        return _buildFavoritesTab();
      case 4:
        return _buildMoreTab();
      default:
        return _buildHomeTab();
    }
  }

  Widget _buildHomeTab() {
    return Padding(
      padding: const EdgeInsets.all(16.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Popularne stacje',
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const SizedBox(height: 16),
          Expanded(
            child: ListView.builder(
              itemCount: _sampleStations.length,
              itemBuilder: (context, index) {
                final station = _sampleStations[index];
                return _buildStationTile(station);
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildRegionsTab() {
    return const Center(
      child: Text(
        'Regiony\n(W przygotowaniu)',
        textAlign: TextAlign.center,
        style: TextStyle(fontSize: 18),
      ),
    );
  }

  Widget _buildWorldTab() {
    return const Center(
      child: Text(
        'Stacje światowe\n(W przygotowaniu)',
        textAlign: TextAlign.center,
        style: TextStyle(fontSize: 18),
      ),
    );
  }

  Widget _buildFavoritesTab() {
    final favorites = _storageService.getFavorites();
    
    if (favorites.isEmpty) {
      return const Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.favorite_outline,
              size: 64,
              color: Colors.grey,
            ),
            SizedBox(height: 16),
            Text(
              'Brak ulubionych stacji',
              style: TextStyle(fontSize: 18, color: Colors.grey),
            ),
            SizedBox(height: 8),
            Text(
              'Dodaj stacje do ulubionych, aby zobaczyć je tutaj',
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.grey),
            ),
          ],
        ),
      );
    }

    return Padding(
      padding: const EdgeInsets.all(16.0),
      child: ListView.builder(
        itemCount: favorites.length,
        itemBuilder: (context, index) {
          final station = favorites[index];
          return _buildStationTile(station);
        },
      ),
    );
  }

  Widget _buildMoreTab() {
    return ListView(
      padding: const EdgeInsets.all(16.0),
      children: [
        ListTile(
          leading: const Icon(Icons.settings),
          title: const Text('Ustawienia'),
          onTap: () {
            // Navigate to settings
          },
        ),
        ListTile(
          leading: const Icon(Icons.info),
          title: const Text('O aplikacji'),
          onTap: () {
            _showAboutDialog();
          },
        ),
        ListTile(
          leading: const Icon(Icons.games),
          title: const Text('Gra Snake'),
          onTap: () {
            // Navigate to Snake game
          },
        ),
        ListTile(
          leading: const Icon(Icons.directions_car),
          title: const Text('Tryb kierowcy'),
          onTap: () {
            // Navigate to car mode
          },
        ),
      ],
    );
  }

  Widget _buildStationTile(RadioStation station) {
    return Card(
      margin: const EdgeInsets.symmetric(vertical: 4),
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: Theme.of(context).colorScheme.primary,
          child: const Icon(
            Icons.radio,
            color: Colors.white,
          ),
        ),
        title: Text(station.name),
        subtitle: Text(station.city),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            IconButton(
              icon: Icon(
                _storageService.isFavorite(station.url)
                    ? Icons.favorite
                    : Icons.favorite_border,
                color: Colors.red,
              ),
              onPressed: () => _toggleFavorite(station),
            ),
            BlocBuilder<RadioPlayerBloc, RadioPlayerState>(
              builder: (context, state) {
                final isCurrentStation = state.playerState.url == station.url;
                final isPlaying = isCurrentStation && state.isPlaying;
                final isBuffering = isCurrentStation && state.isBuffering;

                if (isBuffering) {
                  return const SizedBox(
                    width: 24,
                    height: 24,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  );
                }

                return IconButton(
                  icon: Icon(
                    isPlaying ? Icons.pause : Icons.play_arrow,
                  ),
                  onPressed: () => _playStation(station, isPlaying),
                );
              },
            ),
          ],
        ),
        onTap: () => _playStation(station, false),
      ),
    );
  }

  void _playStation(RadioStation station, bool isCurrentlyPlaying) {
    final radioPlayerBloc = context.read<RadioPlayerBloc>();
    
    if (isCurrentlyPlaying) {
      radioPlayerBloc.add(const PausePlaybackEvent());
    } else {
      radioPlayerBloc.add(PlayStationEvent(station));
    }
  }

  void _toggleFavorite(RadioStation station) async {
    if (_storageService.isFavorite(station.url)) {
      await _storageService.removeFromFavorites(station.url);
    } else {
      await _storageService.addToFavorites(station);
    }
    setState(() {}); // Refresh UI
  }

  void _toggleTheme() async {
    final currentMode = _storageService.getNightMode();
    await _storageService.setNightMode(!currentMode);
    
    // Restart app or update theme
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Restart aplikację, aby zastosować nowy motyw'),
          duration: Duration(seconds: 2),
        ),
      );
    }
  }

  void _showAboutDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('O aplikacji'),
        content: const Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('PulseFM'),
            SizedBox(height: 8),
            Text('Wersja: 10.0.8'),
            SizedBox(height: 8),
            Text('Oficjalna aplikacja od Stackflow Studios'),
            SizedBox(height: 8),
            Text('Wszystkie polskie stacje radiowe w jednym miejscu – szybko, wygodnie i bezpośrednio.'),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Zamknij'),
          ),
        ],
      ),
    );
  }
}