import 'dart:developer';
import 'package:dio/dio.dart';
import 'package:connectivity_plus/connectivity_plus.dart';
import '../models/models.dart';

class ApiService {
  static const String _baseUrl = 'https://api.stackflow.pl/__api/radio24';
  static const Duration _connectTimeout = Duration(seconds: 30);
  static const Duration _receiveTimeout = Duration(seconds: 30);
  static const int _maxRetries = 3;
  
  final Dio _dio;
  final Connectivity _connectivity = Connectivity();

  ApiService() : _dio = Dio() {
    _dio.options.baseUrl = _baseUrl;
    _dio.options.connectTimeout = _connectTimeout;
    _dio.options.receiveTimeout = _receiveTimeout;
    _dio.options.headers = {
      'User-Agent': 'PulseFM/1.0 (Flutter)',
      'Accept': 'application/json',
    };

    // Add interceptor for logging and retry logic
    _dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) {
        log('API Request: ${options.method} ${options.uri}');
        handler.next(options);
      },
      onResponse: (response, handler) {
        log('API Response: ${response.statusCode} ${response.requestOptions.uri}');
        handler.next(response);
      },
      onError: (error, handler) {
        log('API Error: ${error.message}');
        handler.next(error);
      },
    ));
  }

  Future<bool> isConnected() async {
    final connectivityResults = await _connectivity.checkConnectivity();
    return connectivityResults.isNotEmpty && 
           connectivityResults.any((result) => result != ConnectivityResult.none);
  }

  Future<T> _makeRequest<T>(
    Future<Response> Function() request,
    T Function(Map<String, dynamic>) parser,
  ) async {
    if (!await isConnected()) {
      throw ApiException('Brak połączenia z internetem');
    }

    for (int attempt = 1; attempt <= _maxRetries; attempt++) {
      try {
        final response = await request();
        
        if (response.statusCode == 200) {
          final data = response.data;
          if (data is Map<String, dynamic>) {
            return parser(data);
          } else if (data is List) {
            return parser({'data': data});
          } else {
            throw ApiException('Nieprawidłowy format odpowiedzi');
          }
        } else {
          throw ApiException('Błąd serwera: ${response.statusCode}');
        }
      } catch (e) {
        if (e is DioException) {
          if (e.type == DioExceptionType.connectionTimeout ||
              e.type == DioExceptionType.receiveTimeout ||
              e.type == DioExceptionType.connectionError) {
            if (attempt < _maxRetries) {
              log('Próba $attempt/$_maxRetries nie powiodła się, ponawiam...');
              await Future.delayed(Duration(seconds: attempt));
              continue;
            }
          }
        }
        
        if (attempt == _maxRetries) {
          if (e is ApiException) {
            rethrow;
          } else {
            throw ApiException('Błąd połączenia: ${e.toString()}');
          }
        }
      }
    }
    
    throw ApiException('Maksymalna liczba prób została przekroczona');
  }

  Future<List<RadioStation>> fetchStations() async {
    return await _makeRequest(
      () => _dio.get('/stations'),
      (data) {
        final List<dynamic> stationsJson = data['data'] ?? data;
        return stationsJson
            .map((json) => RadioStation.fromJson(json as Map<String, dynamic>))
            .toList();
      },
    );
  }

  Future<List<Wojewodztwo>> fetchOkolice() async {
    return await _makeRequest(
      () => _dio.get('/okolice'),
      (data) {
        final List<dynamic> okolicaJson = data['data'] ?? data;
        return okolicaJson
            .map((json) => Wojewodztwo.fromJson(json as Map<String, dynamic>))
            .toList();
      },
    );
  }

  Future<List<Swiatowe>> fetchSwiat() async {
    return await _makeRequest(
      () => _dio.get('/swiat'),
      (data) {
        final List<dynamic> swiatJson = data['data'] ?? data;
        return swiatJson
            .map((json) => Swiatowe.fromJson(json as Map<String, dynamic>))
            .toList();
      },
    );
  }

  Future<List<RadioStation>> fetchTop10pop() async {
    return await _makeRequest(
      () => _dio.get('/top10pop'),
      (data) {
        final List<dynamic> top10Json = data['data'] ?? data;
        return top10Json
            .map((json) => RadioStation.fromJson(json as Map<String, dynamic>))
            .toList();
      },
    );
  }

  void dispose() {
    _dio.close();
  }
}

class ApiException implements Exception {
  final String message;
  const ApiException(this.message);

  @override
  String toString() => 'ApiException: $message';
}