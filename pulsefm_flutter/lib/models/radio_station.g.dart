// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'radio_station.dart';

// **************************************************************************
// TypeAdapterGenerator
// **************************************************************************

class RadioStationAdapter extends TypeAdapter<RadioStation> {
  @override
  final int typeId = 0;

  @override
  RadioStation read(BinaryReader reader) {
    final numOfFields = reader.readByte();
    final fields = <int, dynamic>{
      for (int i = 0; i < numOfFields; i++) reader.readByte(): reader.read(),
    };
    return RadioStation(
      name: fields[0] as String,
      url: fields[1] as String,
      city: fields[2] as String,
      icon: fields[3] as String?,
    );
  }

  @override
  void write(BinaryWriter writer, RadioStation obj) {
    writer
      ..writeByte(4)
      ..writeByte(0)
      ..write(obj.name)
      ..writeByte(1)
      ..write(obj.url)
      ..writeByte(2)
      ..write(obj.city)
      ..writeByte(3)
      ..write(obj.icon);
  }

  @override
  int get hashCode => typeId.hashCode;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is RadioStationAdapter &&
          runtimeType == other.runtimeType &&
          typeId == other.typeId;
}

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

RadioStation _$RadioStationFromJson(Map<String, dynamic> json) => RadioStation(
      name: json['name'] as String,
      url: json['url'] as String,
      city: json['city'] as String? ?? '',
      icon: json['icon'] as String?,
    );

Map<String, dynamic> _$RadioStationToJson(RadioStation instance) =>
    <String, dynamic>{
      'name': instance.name,
      'url': instance.url,
      'city': instance.city,
      'icon': instance.icon,
    };
