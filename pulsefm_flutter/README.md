# PulseFM Flutter

Oficjalna aplikacja PulseFM przepisana z natywnego Androida na Flutter od **Stackflow Studios**.

## 🎯 Opis

PulseFM to aplikacja radiowa, która umożliwia słuchanie polskich i światowych stacji radiowych. Wszystkie polskie stacje radiowe w jednym miejscu – szybko, wygodnie i bezpośrednio.

## ✨ Funkcje

- 📻 Odtwarzanie stacji radiowych z całego świata
- 🌙 Tryb ciemny i jasny
- 📍 Stacje radiowe przydzielone do województw polskich
- ❤️ Dodawanie stacji do ulubionych
- 🎵 Odtwarzanie w tle z obsługą powiadomień
- 🔄 Automatyczne wznawianie po utracie połączenia
- 💾 Cache stacji radiowych dla trybu offline
- 🎨 Nowoczesny interfejs Material Design 3

## 🏗️ Architektura

Aplikacja została zbudowana z wykorzystaniem:

### State Management
- **BLoC Pattern** - zarządzanie stanem aplikacji
- **Hydrated BLoC** - persistentny stan aplikacji

### Audio
- **just_audio** - odtwarzanie strumieni audio
- **audio_service** - obsługa odtwarzania w tle
- **just_audio_background** - powiadomienia i kontrola mediów

### Sieć
- **Dio** - obsługa HTTP z interceptorami
- **connectivity_plus** - monitorowanie połączenia internetowego

### Przechowywanie danych
- **Hive** - baza danych NoSQL dla cache i ulubionych
- **shared_preferences** - ustawienia aplikacji

### UI/UX
- **Material Design 3** - nowoczesny design system
- **Shimmer** - efekty ładowania
- **cached_network_image** - cache obrazów

## 📁 Struktura projektu

```
lib/
├── bloc/                    # State management (BLoC)
│   └── radio_player/       # Zarządzanie odtwarzaczem
├── models/                 # Modele danych
│   ├── radio_station.dart  # Model stacji radiowej
│   ├── player_state.dart   # Stan odtwarzacza
│   ├── wojewodztwo.dart    # Model województwa
│   └── swiatowe.dart       # Model stacji światowych
├── screens/                # Ekrany aplikacji
│   ├── splash_screen.dart  # Ekran powitalny
│   └── main_screen.dart    # Główny ekran
├── services/               # Usługi aplikacji
│   ├── audio_service.dart  # Obsługa audio
│   ├── api_service.dart    # Komunikacja z API
│   └── storage_service.dart # Przechowywanie danych
├── utils/                  # Narzędzia
│   └── app_theme.dart     # Konfiguracja motywów
├── widgets/               # Komponenty UI
│   └── player_widget.dart # Widget odtwarzacza
└── main.dart             # Punkt wejścia aplikacji
```

## 🚀 Uruchomienie

### Wymagania

- Flutter SDK 3.24.5+
- Dart 3.5.4+
- Android SDK (dla buildu Android)
- iOS SDK (dla buildu iOS)

### Instalacja

1. Sklonuj repozytorium:
```bash
git clone <repository-url>
cd pulsefm_flutter
```

2. Zainstaluj zależności:
```bash
flutter pub get
```

3. Wygeneruj kod dla modeli:
```bash
flutter packages pub run build_runner build
```

4. Uruchom aplikację:
```bash
flutter run
```

## 📋 Zależności

### Główne

- **flutter_bloc**: State management
- **just_audio**: Odtwarzanie audio
- **audio_service**: Usługi audio w tle
- **dio**: HTTP client
- **hive**: Baza danych NoSQL
- **connectivity_plus**: Monitorowanie sieci
- **cached_network_image**: Cache obrazów
- **permission_handler**: Zarządzanie uprawnieniami

### Development

- **build_runner**: Generator kodu
- **json_serializable**: Serializacja JSON
- **hive_generator**: Generator Hive

## 🔧 Konfiguracja

### Android

Dodaj następujące uprawnienia do `android/app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
```

### iOS

Dodaj do `ios/Runner/Info.plist`:

```xml
<key>UIBackgroundModes</key>
<array>
    <string>audio</string>
</array>
```

## 🎵 API Endpoints

Aplikacja korzysta z API Stackflow:

- `GET /stations` - Lista wszystkich stacji
- `GET /okolice` - Stacje według województw
- `GET /swiat` - Stacje światowe
- `GET /top10pop` - Najpopularniejsze stacje

Base URL: `https://api.stackflow.pl/__api/radio24`

## 📱 Platformy

- ✅ Android 6.0+ (API 23+)
- ✅ iOS 12.0+
- 🔄 Web (w przygotowaniu)
- 🔄 Desktop (w przygotowaniu)

## 🔮 Planowane funkcje

- 🚗 Tryb kierowcy (Car Mode)
- 🐍 Gra Snake
- 🔥 Integracja z Firebase
- 📊 Google Ads
- 🏠 Widget na ekran główny
- 🎚️ Kontrola głośności
- ⏰ Sleep timer
- 🎯 Personalizowane rekomendacje

## 🤝 Współpraca

Dołóż swoją cegiełkę do projektu:

1. Fork projektu
2. Stwórz branch z nową funkcją (`git checkout -b feature/AmazingFeature`)
3. Commit zmian (`git commit -m 'Add some AmazingFeature'`)
4. Push do brancha (`git push origin feature/AmazingFeature`)
5. Otwórz Pull Request

## 📝 Licencja

Projekt jest dostępny na licencji [MIT License](LICENSE).

## 📞 Kontakt

**Stackflow Studios**
- Discord: [Serwer Discord](https://discord.gg/MtPs7WXyJu)
- Email: [support@stackflow.pl](mailto:support@stackflow.pl)

---

**PulseFM Flutter** – Twoje ulubione stacje radiowe w jednym kliknięciu, teraz na wszystkich platformach!

## 🔧 Status konwersji

### ✅ Ukończone
- [x] Podstawowa struktura projektu Flutter
- [x] Modele danych (RadioStation, PlayerState, itd.)
- [x] Serwis audio z obsługą strumieniowania
- [x] API service do komunikacji z serwerem
- [x] Local storage (Hive + SharedPreferences)
- [x] BLoC state management
- [x] Ekran powitalny (SplashScreen)
- [x] Główny ekran z nawigacją
- [x] Widget odtwarzacza
- [x] Obsługa uprawnień
- [x] Tryb ciemny/jasny
- [x] System ulubionych stacji

### 🔄 W trakcie
- [ ] Pełna integracja Firebase
- [ ] Google Ads
- [ ] Testy jednostkowe

### 📋 Do zrobienia
- [ ] Tryb kierowcy (CarActivity)
- [ ] Gra Snake
- [ ] Widgety na ekran główny
- [ ] Obsługa metadanych ICY
- [ ] Sleep timer
- [ ] Eksport/import ulubionych
- [ ] Ciemny/jasny motyw per stacja
- [ ] Equalizier audio
- [ ] Cache audio dla offline

## 🏗️ Różnice względem natywnej wersji Android

### Zalety Flutter
1. **Cross-platform** - jedna baza kodu dla Android, iOS, Web, Desktop
2. **Nowoczesna architektura** - BLoC pattern zamiast Android Activities
3. **Lepsze zarządzanie stanem** - Hydrated BLoC z persistencją
4. **Declarative UI** - łatwiejsze w utrzymaniu niż XML layouts
5. **Hot reload** - szybsze development
6. **Lepsze testy** - wbudowane wsparcie dla testowania

### Zachowane funkcje
- Wszystkie podstawowe funkcje radio
- System ulubionych
- Cache stacji
- Odtwarzanie w tle
- Obsługa powiadomień
- Tryb offline
- API kompatybilność

### Ulepszone funkcje
- Płynniejsze animacje
- Lepsze zarządzanie pamięcią
- Automatyczne zarządzanie lifecycle
- Typowanie statyczne Dart
- Nowoczesny Material Design 3
