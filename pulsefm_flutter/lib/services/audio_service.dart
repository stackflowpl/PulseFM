import 'dart:async';
import 'dart:developer';
import 'package:audio_service/audio_service.dart';
import 'package:just_audio/just_audio.dart';
import 'package:just_audio_background/just_audio_background.dart';
import '../models/models.dart' as models;

class RadioAudioHandler extends BaseAudioHandler {
  static const String _userAgent = 'PulseFM/1.0 (Flutter)';
  
  final AudioPlayer _player = AudioPlayer();
  final StreamController<models.PlayerState> _playerStateController = 
      StreamController<models.PlayerState>.broadcast();
  
  models.RadioStation? _currentStation;
  bool _isConnected = true;
  Timer? _retryTimer;
  int _retryCount = 0;
  static const int _maxRetries = 3;
  static const Duration _retryDelay = Duration(seconds: 2);

  Stream<models.PlayerState> get playerStateStream => _playerStateController.stream;
  
  // Store current player state
  models.PlayerState _currentPlayerState = const models.PlayerState();
  models.PlayerState get currentPlayerState => _currentPlayerState;

  RadioAudioHandler() {
    _init();
  }

  void _init() {
    // Listen to player state changes
    _player.playerStateStream.listen((playerState) {
      _updatePlayerState();
    });

    // Listen to processing state changes
    _player.processingStateStream.listen((processingState) {
      _updatePlayerState();
      
      if (processingState == ProcessingState.completed ||
          processingState == ProcessingState.idle) {
        _handlePlaybackError();
      }
    });

    // Listen to position stream for metadata updates
    _player.positionStream.listen((_) {
      // Position updates can trigger metadata refresh
    });

    // Set initial audio session configuration
    _player.setAudioSource(
      AudioSource.uri(
        Uri.parse(''),
        headers: {'User-Agent': _userAgent},
      ),
    ).catchError((error) {
      log('Initial audio source setup error: $error');
      return Duration.zero; // Return a default Duration
    });
  }

  Future<void> playStation(models.RadioStation station) async {
    try {
      _currentStation = station;
      _retryCount = 0;
      _retryTimer?.cancel();

      // Update media item
      mediaItem.add(MediaItem(
        id: station.url,
        album: station.city.isNotEmpty ? station.city : 'Radio',
        title: station.name,
        artist: 'PulseFM',
        artUri: station.icon != null ? Uri.parse(station.icon!) : null,
      ));

      // Set audio source with custom headers
      await _player.setAudioSource(
        AudioSource.uri(
          Uri.parse(station.url),
          headers: {
            'User-Agent': _userAgent,
            'Icy-MetaData': '1',
          },
        ),
      );

      // Start playback
      await _player.play();
      
      _updatePlayerState();
      
    } catch (error) {
      log('Error playing station ${station.name}: $error');
      _handlePlaybackError();
    }
  }

  @override
  Future<void> play() async {
    if (_currentStation != null) {
      if (_player.processingState == ProcessingState.idle ||
          _player.processingState == ProcessingState.completed) {
        await playStation(_currentStation!);
      } else {
        await _player.play();
      }
    }
  }

  @override
  Future<void> pause() async {
    await _player.pause();
    _updatePlayerState();
  }

  @override
  Future<void> stop() async {
    await _player.stop();
    _currentStation = null;
    _retryTimer?.cancel();
    _retryCount = 0;
    _updatePlayerState();
  }

  void _updatePlayerState() {
    final isPlaying = _player.playing;
    final isBuffering = _player.processingState == ProcessingState.buffering ||
                       _player.processingState == ProcessingState.loading;

    final state = models.PlayerState(
      isPlaying: isPlaying,
      isBuffering: isBuffering,
      url: _currentStation?.url ?? '',
      city: _currentStation?.city ?? '',
      stationName: _currentStation?.name ?? '',
      icon: _currentStation?.icon,
      trackTitle: null, // Will be updated from ICY metadata
      trackArtist: null, // Will be updated from ICY metadata
    );

    _currentPlayerState = state;
    if (!_playerStateController.isClosed) {
      _playerStateController.add(state);
    }

    // Update playback state for system UI
    playbackState.add(PlaybackState(
      controls: [
        if (isPlaying) MediaControl.pause else MediaControl.play,
        MediaControl.stop,
      ],
      systemActions: const {
        MediaAction.seek,
        MediaAction.seekForward,
        MediaAction.seekBackward,
      },
      androidCompactActionIndices: const [0, 1],
      processingState: _getAudioProcessingState(),
      playing: isPlaying,
      updatePosition: _player.position,
      bufferedPosition: _player.bufferedPosition,
    ));
  }

  AudioProcessingState _getAudioProcessingState() {
    switch (_player.processingState) {
      case ProcessingState.idle:
        return AudioProcessingState.idle;
      case ProcessingState.loading:
        return AudioProcessingState.loading;
      case ProcessingState.buffering:
        return AudioProcessingState.buffering;
      case ProcessingState.ready:
        return AudioProcessingState.ready;
      case ProcessingState.completed:
        return AudioProcessingState.completed;
    }
  }

  void _handlePlaybackError() {
    if (_currentStation != null && _retryCount < _maxRetries && _isConnected) {
      _retryCount++;
      log('Retrying playback for ${_currentStation!.name} (attempt $_retryCount/$_maxRetries)');
      
      _retryTimer = Timer(_retryDelay, () async {
        try {
          await playStation(_currentStation!);
        } catch (error) {
          log('Retry failed: $error');
          _handlePlaybackError();
        }
      });
    } else {
      log('Max retries reached or no connection. Stopping playback.');
      stop();
    }
  }

  void setConnectionStatus(bool isConnected) {
    _isConnected = isConnected;
    if (!isConnected) {
      _retryTimer?.cancel();
    }
  }

  @override
  Future<void> onTaskRemoved() async {
    // Handle task removal - keep playing or stop based on user preference
    await stop();
  }

  @override
  Future<void> onNotificationDeleted() async {
    await stop();
  }

  void dispose() {
    _retryTimer?.cancel();
    _player.dispose();
    _playerStateController.close();
  }
}

class RadioAudioService {
  static RadioAudioHandler? _handler;
  static bool _initialized = false;

  static Future<void> init() async {
    if (_initialized) return;

    JustAudioBackground.init(
      androidNotificationChannelId: 'net.gf.radio24.channel.audio',
      androidNotificationChannelName: 'PulseFM',
      androidNotificationOngoing: true,
      androidStopForegroundOnPause: false,
    );

    _handler = await AudioService.init(
      builder: () => RadioAudioHandler(),
      config: const AudioServiceConfig(
        androidNotificationChannelId: 'net.gf.radio24.channel.audio',
        androidNotificationChannelName: 'PulseFM',
        androidNotificationOngoing: true,
        androidStopForegroundOnPause: false,
        androidNotificationIcon: 'drawable/ic_stat_radio',
        fastForwardInterval: Duration(seconds: 10),
        rewindInterval: Duration(seconds: 10),
      ),
    );

    _initialized = true;
  }

  static RadioAudioHandler get handler {
    if (_handler == null) {
      throw Exception('RadioAudioService not initialized. Call init() first.');
    }
    return _handler!;
  }

  static bool get isInitialized => _initialized;
}