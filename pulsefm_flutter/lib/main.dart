import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
// import 'package:firebase_core/firebase_core.dart';
// import 'package:firebase_crashlytics/firebase_crashlytics.dart';
// import 'package:firebase_performance/firebase_performance.dart';
// import 'package:google_mobile_ads/google_mobile_ads.dart';

import 'services/storage_service.dart';
import 'services/audio_service.dart';
import 'bloc/radio_player/radio_player_bloc.dart';
import 'bloc/radio_player/radio_player_state.dart';
import 'screens/splash_screen.dart';
import 'utils/app_theme.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Initialize Firebase - commented out for now
  // await Firebase.initializeApp();
  
  // Set up crashlytics - commented out for now
  // FlutterError.onError = FirebaseCrashlytics.instance.recordFlutterFatalError;
  // PlatformDispatcher.instance.onError = (error, stack) {
  //   FirebaseCrashlytics.instance.recordError(error, stack, fatal: true);
  //   return true;
  // };

  // Initialize Firebase Performance - commented out for now
  // await FirebasePerformance.instance.setPerformanceCollectionEnabled(true);

  // Initialize Google Mobile Ads - commented out for now
  // await MobileAds.instance.initialize();

  // Initialize storage service
  await StorageService.instance.init();

  // Initialize audio service
  await RadioAudioService.init();

  // Set system UI overlay style
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.dark,
      systemNavigationBarColor: Colors.white,
      systemNavigationBarIconBrightness: Brightness.dark,
    ),
  );

  runApp(const PulseFMApp());
}

class PulseFMApp extends StatelessWidget {
  const PulseFMApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiBlocProvider(
      providers: [
        BlocProvider(
          create: (context) => RadioPlayerBloc(),
        ),
      ],
      child: BlocBuilder<RadioPlayerBloc, RadioPlayerState>(
        buildWhen: (previous, current) => 
            previous.playerState != current.playerState,
        builder: (context, state) {
          // Determine if we should use dark theme
          final isDarkMode = StorageService.instance.getNightMode();
          
          return MaterialApp(
            title: 'PulseFM',
            debugShowCheckedModeBanner: false,
            theme: AppTheme.lightTheme,
            darkTheme: AppTheme.darkTheme,
            themeMode: isDarkMode ? ThemeMode.dark : ThemeMode.light,
            home: const SplashScreen(),
            builder: (context, child) {
              return MediaQuery(
                data: MediaQuery.of(context).copyWith(
                  textScaler: const TextScaler.linear(1.0), // Prevent system font scaling
                ),
                child: child!,
              );
            },
          );
        },
      ),
    );
  }
}
