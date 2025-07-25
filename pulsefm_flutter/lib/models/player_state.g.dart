// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'player_state.dart';

// **************************************************************************
// TypeAdapterGenerator
// **************************************************************************

class PlayerStateAdapter extends TypeAdapter<PlayerState> {
  @override
  final int typeId = 1;

  @override
  PlayerState read(BinaryReader reader) {
    final numOfFields = reader.readByte();
    final fields = <int, dynamic>{
      for (int i = 0; i < numOfFields; i++) reader.readByte(): reader.read(),
    };
    return PlayerState(
      isPlaying: fields[0] as bool,
      isBuffering: fields[1] as bool,
      url: fields[2] as String,
      city: fields[3] as String,
      stationName: fields[4] as String,
      icon: fields[5] as String?,
      trackTitle: fields[6] as String?,
      trackArtist: fields[7] as String?,
    );
  }

  @override
  void write(BinaryWriter writer, PlayerState obj) {
    writer
      ..writeByte(8)
      ..writeByte(0)
      ..write(obj.isPlaying)
      ..writeByte(1)
      ..write(obj.isBuffering)
      ..writeByte(2)
      ..write(obj.url)
      ..writeByte(3)
      ..write(obj.city)
      ..writeByte(4)
      ..write(obj.stationName)
      ..writeByte(5)
      ..write(obj.icon)
      ..writeByte(6)
      ..write(obj.trackTitle)
      ..writeByte(7)
      ..write(obj.trackArtist);
  }

  @override
  int get hashCode => typeId.hashCode;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is PlayerStateAdapter &&
          runtimeType == other.runtimeType &&
          typeId == other.typeId;
}

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PlayerState _$PlayerStateFromJson(Map<String, dynamic> json) => PlayerState(
      isPlaying: json['isPlaying'] as bool? ?? false,
      isBuffering: json['isBuffering'] as bool? ?? false,
      url: json['url'] as String? ?? '',
      city: json['city'] as String? ?? '',
      stationName: json['stationName'] as String? ?? '',
      icon: json['icon'] as String?,
      trackTitle: json['trackTitle'] as String?,
      trackArtist: json['trackArtist'] as String?,
    );

Map<String, dynamic> _$PlayerStateToJson(PlayerState instance) =>
    <String, dynamic>{
      'isPlaying': instance.isPlaying,
      'isBuffering': instance.isBuffering,
      'url': instance.url,
      'city': instance.city,
      'stationName': instance.stationName,
      'icon': instance.icon,
      'trackTitle': instance.trackTitle,
      'trackArtist': instance.trackArtist,
    };
