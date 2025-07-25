import 'package:json_annotation/json_annotation.dart';
import 'radio_station.dart';

part 'wojewodztwo.g.dart';

@JsonSerializable()
class Wojewodztwo {
  final String woj;
  final String icon;
  final List<RadioStation> stations;

  const Wojewodztwo({
    required this.woj,
    required this.icon,
    required this.stations,
  });

  factory Wojewodztwo.fromJson(Map<String, dynamic> json) =>
      _$WojewodztwoFromJson(json);

  Map<String, dynamic> toJson() => _$WojewodztwoToJson(this);

  Wojewodztwo copyWith({
    String? woj,
    String? icon,
    List<RadioStation>? stations,
  }) {
    return Wojewodztwo(
      woj: woj ?? this.woj,
      icon: icon ?? this.icon,
      stations: stations ?? this.stations,
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is Wojewodztwo &&
        other.woj == woj &&
        other.icon == icon &&
        other.stations == stations;
  }

  @override
  int get hashCode {
    return woj.hashCode ^ icon.hashCode ^ stations.hashCode;
  }

  @override
  String toString() {
    return 'Wojewodztwo(woj: $woj, icon: $icon, stations: ${stations.length} stations)';
  }
}