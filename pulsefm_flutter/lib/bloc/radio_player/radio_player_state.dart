import 'package:equatable/equatable.dart';
import '../../models/models.dart' as models;

class RadioPlayerState extends Equatable {
  final models.PlayerState playerState;
  final double volume;
  final bool isConnected;
  final String? errorMessage;

  const RadioPlayerState({
    this.playerState = const models.PlayerState(),
    this.volume = 1.0,
    this.isConnected = true,
    this.errorMessage,
  });

  RadioPlayerState copyWith({
    models.PlayerState? playerState,
    double? volume,
    bool? isConnected,
    String? errorMessage,
  }) {
    return RadioPlayerState(
      playerState: playerState ?? this.playerState,
      volume: volume ?? this.volume,
      isConnected: isConnected ?? this.isConnected,
      errorMessage: errorMessage,
    );
  }

  RadioPlayerState clearError() {
    return copyWith(errorMessage: null);
  }

  bool get isPlaying => playerState.isPlaying;
  bool get isBuffering => playerState.isBuffering;
  bool get hasCurrentStation => playerState.stationName.isNotEmpty;
  String get currentStationName => playerState.stationName;
  String get currentStationCity => playerState.city;
  String get currentTrackInfo => playerState.trackInfo;

  @override
  List<Object?> get props => [
        playerState,
        volume,
        isConnected,
        errorMessage,
      ];

  @override
  String toString() {
    return 'RadioPlayerState(playerState: $playerState, volume: $volume, isConnected: $isConnected, errorMessage: $errorMessage)';
  }
}