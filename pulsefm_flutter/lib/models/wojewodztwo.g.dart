// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'wojewodztwo.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

Wojewodztwo _$WojewodztwoFromJson(Map<String, dynamic> json) => Wojewodztwo(
      woj: json['woj'] as String,
      icon: json['icon'] as String,
      stations: (json['stations'] as List<dynamic>)
          .map((e) => RadioStation.fromJson(e as Map<String, dynamic>))
          .toList(),
    );

Map<String, dynamic> _$WojewodztwoToJson(Wojewodztwo instance) =>
    <String, dynamic>{
      'woj': instance.woj,
      'icon': instance.icon,
      'stations': instance.stations,
    };
