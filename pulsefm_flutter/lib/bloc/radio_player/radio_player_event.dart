import 'package:equatable/equatable.dart';
import '../../models/models.dart' as models;

abstract class RadioPlayerEvent extends Equatable {
  const RadioPlayerEvent();

  @override
  List<Object?> get props => [];
}

class PlayStationEvent extends RadioPlayerEvent {
  final models.RadioStation station;

  const PlayStationEvent(this.station);

  @override
  List<Object?> get props => [station];
}

class PausePlaybackEvent extends RadioPlayerEvent {
  const PausePlaybackEvent();
}

class ResumePlaybackEvent extends RadioPlayerEvent {
  const ResumePlaybackEvent();
}

class StopPlaybackEvent extends RadioPlayerEvent {
  const StopPlaybackEvent();
}

class PlayerStateUpdateEvent extends RadioPlayerEvent {
  final models.PlayerState playerState;

  const PlayerStateUpdateEvent(this.playerState);

  @override
  List<Object?> get props => [playerState];
}

class SetVolumeEvent extends RadioPlayerEvent {
  final double volume;

  const SetVolumeEvent(this.volume);

  @override
  List<Object?> get props => [volume];
}

class UpdateConnectionStatusEvent extends RadioPlayerEvent {
  final bool isConnected;

  const UpdateConnectionStatusEvent(this.isConnected);

  @override
  List<Object?> get props => [isConnected];
}