import 'package:json_annotation/json_annotation.dart';
import 'radio_station.dart';

part 'swiatowe.g.dart';

@JsonSerializable()
class Swiatowe {
  final String country;
  final String icon;
  final List<RadioStation> stations;

  const Swiatowe({
    required this.country,
    required this.icon,
    required this.stations,
  });

  factory Swiatowe.fromJson(Map<String, dynamic> json) =>
      _$SwiatoweFromJson(json);

  Map<String, dynamic> toJson() => _$SwiatoweToJson(this);

  Swiatowe copyWith({
    String? country,
    String? icon,
    List<RadioStation>? stations,
  }) {
    return Swiatowe(
      country: country ?? this.country,
      icon: icon ?? this.icon,
      stations: stations ?? this.stations,
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is Swiatowe &&
        other.country == country &&
        other.icon == icon &&
        other.stations == stations;
  }

  @override
  int get hashCode {
    return country.hashCode ^ icon.hashCode ^ stations.hashCode;
  }

  @override
  String toString() {
    return 'Swiatowe(country: $country, icon: $icon, stations: ${stations.length} stations)';
  }
}