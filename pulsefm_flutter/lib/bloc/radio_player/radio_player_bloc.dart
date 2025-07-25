import 'dart:async';
import 'dart:developer';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:connectivity_plus/connectivity_plus.dart';
import '../../services/audio_service.dart';
import '../../services/storage_service.dart';
import '../../models/models.dart' as models;
import 'radio_player_event.dart';
import 'radio_player_state.dart';

class RadioPlayerBloc extends Bloc<RadioPlayerEvent, RadioPlayerState> {
  final StorageService _storageService;
  late StreamSubscription<models.PlayerState> _playerStateSubscription;
  late StreamSubscription<List<ConnectivityResult>> _connectivitySubscription;

  RadioPlayerBloc({
    StorageService? storageService,
  })  : _storageService = storageService ?? StorageService.instance,
        super(const RadioPlayerState()) {
    
    on<PlayStationEvent>(_onPlayStation);
    on<PausePlaybackEvent>(_onPausePlayback);
    on<ResumePlaybackEvent>(_onResumePlayback);
    on<StopPlaybackEvent>(_onStopPlayback);
    on<PlayerStateUpdateEvent>(_onPlayerStateUpdate);
    on<SetVolumeEvent>(_onSetVolume);
    on<UpdateConnectionStatusEvent>(_onUpdateConnectionStatus);

    _init();
  }

  void _init() async {
    // Initialize audio service if not already done
    if (!RadioAudioService.isInitialized) {
      await RadioAudioService.init();
    }

    // Listen to player state changes
    _playerStateSubscription = RadioAudioService.handler.playerStateStream.listen(
      (playerState) {
        add(PlayerStateUpdateEvent(playerState));
      },
    );

    // Listen to connectivity changes
    _connectivitySubscription = Connectivity().onConnectivityChanged.listen(
      (List<ConnectivityResult> results) {
        final isConnected = results.isNotEmpty && results.any((result) => result != ConnectivityResult.none);
        add(UpdateConnectionStatusEvent(isConnected));
        RadioAudioService.handler.setConnectionStatus(isConnected);
      },
    );

    // Restore saved player state if available
    final savedState = _storageService.getPlayerState();
    if (savedState != null) {
      add(PlayerStateUpdateEvent(savedState));
    }
  }

  Future<void> _onPlayStation(
    PlayStationEvent event,
    Emitter<RadioPlayerState> emit,
  ) async {
    try {
      log('Playing station: ${event.station.name}');
      
      if (!state.isConnected) {
        emit(state.copyWith(
          errorMessage: 'Brak połączenia z internetem',
        ));
        return;
      }

      await RadioAudioService.handler.playStation(event.station);
      
      // Clear any previous errors
      emit(state.clearError());
      
    } catch (error) {
      log('Error playing station: $error');
      emit(state.copyWith(
        errorMessage: 'Błąd podczas odtwarzania stacji: $error',
      ));
    }
  }

  Future<void> _onPausePlayback(
    PausePlaybackEvent event,
    Emitter<RadioPlayerState> emit,
  ) async {
    try {
      await RadioAudioService.handler.pause();
      emit(state.clearError());
    } catch (error) {
      log('Error pausing playback: $error');
      emit(state.copyWith(
        errorMessage: 'Błąd podczas pauzowania odtwarzania',
      ));
    }
  }

  Future<void> _onResumePlayback(
    ResumePlaybackEvent event,
    Emitter<RadioPlayerState> emit,
  ) async {
    try {
      if (!state.isConnected) {
        emit(state.copyWith(
          errorMessage: 'Brak połączenia z internetem',
        ));
        return;
      }

      await RadioAudioService.handler.play();
      emit(state.clearError());
    } catch (error) {
      log('Error resuming playback: $error');
      emit(state.copyWith(
        errorMessage: 'Błąd podczas wznawiania odtwarzania',
      ));
    }
  }

  Future<void> _onStopPlayback(
    StopPlaybackEvent event,
    Emitter<RadioPlayerState> emit,
  ) async {
    try {
      await RadioAudioService.handler.stop();
      emit(state.clearError());
    } catch (error) {
      log('Error stopping playback: $error');
      emit(state.copyWith(
        errorMessage: 'Błąd podczas zatrzymywania odtwarzania',
      ));
    }
  }

  Future<void> _onPlayerStateUpdate(
    PlayerStateUpdateEvent event,
    Emitter<RadioPlayerState> emit,
  ) async {
    emit(state.copyWith(playerState: event.playerState));
    
    // Save player state to storage
    await _storageService.savePlayerState(event.playerState);
  }

  Future<void> _onSetVolume(
    SetVolumeEvent event,
    Emitter<RadioPlayerState> emit,
  ) async {
    final clampedVolume = event.volume.clamp(0.0, 1.0);
    emit(state.copyWith(volume: clampedVolume));
    
    // Note: Volume control would be implemented in the audio service
    // For now, we just update the state
  }

  Future<void> _onUpdateConnectionStatus(
    UpdateConnectionStatusEvent event,
    Emitter<RadioPlayerState> emit,
  ) async {
    emit(state.copyWith(isConnected: event.isConnected));
    
    if (!event.isConnected && state.isPlaying) {
      emit(state.copyWith(
        errorMessage: 'Utracono połączenie z internetem',
      ));
    }
  }

  void clearError() {
    add(PlayerStateUpdateEvent(state.playerState));
  }

  @override
  Future<void> close() {
    _playerStateSubscription.cancel();
    _connectivitySubscription.cancel();
    return super.close();
  }
}