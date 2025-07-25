import 'package:json_annotation/json_annotation.dart';
import 'package:hive/hive.dart';

part 'player_state.g.dart';

@JsonSerializable()
@HiveType(typeId: 1)
class PlayerState {
  @HiveField(0)
  final bool isPlaying;
  
  @HiveField(1)
  final bool isBuffering;
  
  @HiveField(2)
  final String url;
  
  @HiveField(3)
  final String city;
  
  @HiveField(4)
  final String stationName;
  
  @HiveField(5)
  final String? icon;
  
  @HiveField(6)
  final String? trackTitle;
  
  @HiveField(7)
  final String? trackArtist;

  const PlayerState({
    this.isPlaying = false,
    this.isBuffering = false,
    this.url = '',
    this.city = '',
    this.stationName = '',
    this.icon,
    this.trackTitle,
    this.trackArtist,
  });

  factory PlayerState.fromJson(Map<String, dynamic> json) =>
      _$PlayerStateFromJson(json);

  Map<String, dynamic> toJson() => _$PlayerStateToJson(this);

  PlayerState copyWith({
    bool? isPlaying,
    bool? isBuffering,
    String? url,
    String? city,
    String? stationName,
    String? icon,
    String? trackTitle,
    String? trackArtist,
  }) {
    return PlayerState(
      isPlaying: isPlaying ?? this.isPlaying,
      isBuffering: isBuffering ?? this.isBuffering,
      url: url ?? this.url,
      city: city ?? this.city,
      stationName: stationName ?? this.stationName,
      icon: icon ?? this.icon,
      trackTitle: trackTitle ?? this.trackTitle,
      trackArtist: trackArtist ?? this.trackArtist,
    );
  }

  String get trackInfo {
    if (trackTitle != null && trackArtist != null) {
      return '$trackArtist - $trackTitle';
    } else if (trackTitle != null) {
      return trackTitle!;
    } else if (trackArtist != null) {
      return trackArtist!;
    }
    return '';
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is PlayerState &&
        other.isPlaying == isPlaying &&
        other.isBuffering == isBuffering &&
        other.url == url &&
        other.city == city &&
        other.stationName == stationName &&
        other.icon == icon &&
        other.trackTitle == trackTitle &&
        other.trackArtist == trackArtist;
  }

  @override
  int get hashCode {
    return isPlaying.hashCode ^
        isBuffering.hashCode ^
        url.hashCode ^
        city.hashCode ^
        stationName.hashCode ^
        icon.hashCode ^
        trackTitle.hashCode ^
        trackArtist.hashCode;
  }

  @override
  String toString() {
    return 'PlayerState(isPlaying: $isPlaying, isBuffering: $isBuffering, stationName: $stationName, city: $city, trackInfo: $trackInfo)';
  }
}