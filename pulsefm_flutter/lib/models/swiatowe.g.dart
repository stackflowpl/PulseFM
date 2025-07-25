// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'swiatowe.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

Swiatowe _$SwiatoweFromJson(Map<String, dynamic> json) => Swiatowe(
      country: json['country'] as String,
      icon: json['icon'] as String,
      stations: (json['stations'] as List<dynamic>)
          .map((e) => RadioStation.fromJson(e as Map<String, dynamic>))
          .toList(),
    );

Map<String, dynamic> _$SwiatoweToJson(Swiatowe instance) => <String, dynamic>{
      'country': instance.country,
      'icon': instance.icon,
      'stations': instance.stations,
    };
