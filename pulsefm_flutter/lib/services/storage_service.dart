import 'dart:convert';
import 'dart:developer';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/models.dart';

class StorageService {
  static const String _favoritesBoxName = 'favorites';
  static const String _playerStateBoxName = 'player_state';
  static const String _stationsBoxName = 'stations';
  static const String _okolicaBoxName = 'okolica';
  static const String _swiatBoxName = 'swiat';
  static const String _top10popBoxName = 'top10pop';
  
  static const String _lastUpdateKey = 'last_update';
  static const String _nightModeKey = 'night_mode';
  static const String _permissionsAcceptedKey = 'permissions_accepted';
  static const String _dataConsentKey = 'data_consent';
  
  static const Duration _cacheValidityDuration = Duration(hours: 24);

  late Box<RadioStation> _favoritesBox;
  late Box<PlayerState> _playerStateBox;
  late Box<String> _stationsBox;
  late Box<String> _okolicaBox;
  late Box<String> _swiatBox;
  late Box<String> _top10popBox;
  late SharedPreferences _prefs;

  static StorageService? _instance;
  static StorageService get instance {
    _instance ??= StorageService._();
    return _instance!;
  }

  StorageService._();

  Future<void> init() async {
    await Hive.initFlutter();
    
    // Register adapters
    if (!Hive.isAdapterRegistered(0)) {
      Hive.registerAdapter(RadioStationAdapter());
    }
    if (!Hive.isAdapterRegistered(1)) {
      Hive.registerAdapter(PlayerStateAdapter());
    }

    // Open boxes
    _favoritesBox = await Hive.openBox<RadioStation>(_favoritesBoxName);
    _playerStateBox = await Hive.openBox<PlayerState>(_playerStateBoxName);
    _stationsBox = await Hive.openBox<String>(_stationsBoxName);
    _okolicaBox = await Hive.openBox<String>(_okolicaBoxName);
    _swiatBox = await Hive.openBox<String>(_swiatBoxName);
    _top10popBox = await Hive.openBox<String>(_top10popBoxName);

    _prefs = await SharedPreferences.getInstance();
  }

  // Favorites management
  Future<void> addToFavorites(RadioStation station) async {
    await _favoritesBox.put(station.url, station);
    log('Added to favorites: ${station.name}');
  }

  Future<void> removeFromFavorites(String stationUrl) async {
    await _favoritesBox.delete(stationUrl);
    log('Removed from favorites: $stationUrl');
  }

  bool isFavorite(String stationUrl) {
    return _favoritesBox.containsKey(stationUrl);
  }

  List<RadioStation> getFavorites() {
    return _favoritesBox.values.toList();
  }

  Stream<BoxEvent> get favoritesStream => _favoritesBox.watch();

  // Player state management
  Future<void> savePlayerState(PlayerState state) async {
    await _playerStateBox.put('current', state);
  }

  PlayerState? getPlayerState() {
    return _playerStateBox.get('current');
  }

  // Cache management for stations data
  Future<void> cacheStations(List<RadioStation> stations) async {
    final jsonString = jsonEncode(stations.map((s) => s.toJson()).toList());
    await _stationsBox.put('data', jsonString);
    await _setLastUpdate(_stationsBoxName);
  }

  Future<List<RadioStation>?> getCachedStations() async {
    if (!_isCacheValid(_stationsBoxName)) return null;
    
    final jsonString = _stationsBox.get('data');
    if (jsonString == null) return null;

    try {
      final List<dynamic> jsonData = jsonDecode(jsonString);
      return jsonData.map((json) => RadioStation.fromJson(json)).toList();
    } catch (e) {
      log('Error parsing cached stations: $e');
      return null;
    }
  }

  Future<void> cacheOkolica(List<Wojewodztwo> okolica) async {
    final jsonString = jsonEncode(okolica.map((w) => w.toJson()).toList());
    await _okolicaBox.put('data', jsonString);
    await _setLastUpdate(_okolicaBoxName);
  }

  Future<List<Wojewodztwo>?> getCachedOkolica() async {
    if (!_isCacheValid(_okolicaBoxName)) return null;
    
    final jsonString = _okolicaBox.get('data');
    if (jsonString == null) return null;

    try {
      final List<dynamic> jsonData = jsonDecode(jsonString);
      return jsonData.map((json) => Wojewodztwo.fromJson(json)).toList();
    } catch (e) {
      log('Error parsing cached okolica: $e');
      return null;
    }
  }

  Future<void> cacheSwiat(List<Swiatowe> swiat) async {
    final jsonString = jsonEncode(swiat.map((s) => s.toJson()).toList());
    await _swiatBox.put('data', jsonString);
    await _setLastUpdate(_swiatBoxName);
  }

  Future<List<Swiatowe>?> getCachedSwiat() async {
    if (!_isCacheValid(_swiatBoxName)) return null;
    
    final jsonString = _swiatBox.get('data');
    if (jsonString == null) return null;

    try {
      final List<dynamic> jsonData = jsonDecode(jsonString);
      return jsonData.map((json) => Swiatowe.fromJson(json)).toList();
    } catch (e) {
      log('Error parsing cached swiat: $e');
      return null;
    }
  }

  Future<void> cacheTop10pop(List<RadioStation> top10pop) async {
    final jsonString = jsonEncode(top10pop.map((s) => s.toJson()).toList());
    await _top10popBox.put('data', jsonString);
    await _setLastUpdate(_top10popBoxName);
  }

  Future<List<RadioStation>?> getCachedTop10pop() async {
    if (!_isCacheValid(_top10popBoxName)) return null;
    
    final jsonString = _top10popBox.get('data');
    if (jsonString == null) return null;

    try {
      final List<dynamic> jsonData = jsonDecode(jsonString);
      return jsonData.map((json) => RadioStation.fromJson(json)).toList();
    } catch (e) {
      log('Error parsing cached top10pop: $e');
      return null;
    }
  }

  // Settings management
  Future<void> setNightMode(bool enabled) async {
    await _prefs.setBool(_nightModeKey, enabled);
  }

  bool getNightMode() {
    return _prefs.getBool(_nightModeKey) ?? false;
  }

  Future<void> setPermissionsAccepted(bool accepted) async {
    await _prefs.setBool(_permissionsAcceptedKey, accepted);
  }

  bool getPermissionsAccepted() {
    return _prefs.getBool(_permissionsAcceptedKey) ?? false;
  }

  Future<void> setDataConsent(bool consent) async {
    await _prefs.setBool(_dataConsentKey, consent);
  }

  bool getDataConsent() {
    return _prefs.getBool(_dataConsentKey) ?? false;
  }

  // Cache validity helpers
  Future<void> _setLastUpdate(String key) async {
    await _prefs.setInt('${_lastUpdateKey}_$key', DateTime.now().millisecondsSinceEpoch);
  }

  bool _isCacheValid(String key) {
    final lastUpdate = _prefs.getInt('${_lastUpdateKey}_$key');
    if (lastUpdate == null) return false;
    
    final lastUpdateTime = DateTime.fromMillisecondsSinceEpoch(lastUpdate);
    return DateTime.now().difference(lastUpdateTime) < _cacheValidityDuration;
  }

  // Clear cache
  Future<void> clearCache() async {
    await _stationsBox.clear();
    await _okolicaBox.clear();
    await _swiatBox.clear();
    await _top10popBox.clear();
    
    // Clear timestamps
    await _prefs.remove('${_lastUpdateKey}_$_stationsBoxName');
    await _prefs.remove('${_lastUpdateKey}_$_okolicaBoxName');
    await _prefs.remove('${_lastUpdateKey}_$_swiatBoxName');
    await _prefs.remove('${_lastUpdateKey}_$_top10popBoxName');
  }

  Future<void> dispose() async {
    await _favoritesBox.close();
    await _playerStateBox.close();
    await _stationsBox.close();
    await _okolicaBox.close();
    await _swiatBox.close();
    await _top10popBox.close();
  }
}