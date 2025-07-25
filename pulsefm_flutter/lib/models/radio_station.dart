import 'package:json_annotation/json_annotation.dart';
import 'package:hive/hive.dart';

part 'radio_station.g.dart';

@JsonSerializable()
@HiveType(typeId: 0)
class RadioStation {
  @HiveField(0)
  final String name;
  
  @HiveField(1)
  final String url;
  
  @HiveField(2)
  final String city;
  
  @HiveField(3)
  final String? icon;

  const RadioStation({
    required this.name,
    required this.url,
    this.city = '',
    this.icon,
  });

  factory RadioStation.fromJson(Map<String, dynamic> json) =>
      _$RadioStationFromJson(json);

  Map<String, dynamic> toJson() => _$RadioStationToJson(this);

  RadioStation copyWith({
    String? name,
    String? url,
    String? city,
    String? icon,
  }) {
    return RadioStation(
      name: name ?? this.name,
      url: url ?? this.url,
      city: city ?? this.city,
      icon: icon ?? this.icon,
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is RadioStation &&
        other.name == name &&
        other.url == url &&
        other.city == city &&
        other.icon == icon;
  }

  @override
  int get hashCode {
    return name.hashCode ^
        url.hashCode ^
        city.hashCode ^
        icon.hashCode;
  }

  @override
  String toString() {
    return 'RadioStation(name: $name, url: $url, city: $city, icon: $icon)';
  }
}