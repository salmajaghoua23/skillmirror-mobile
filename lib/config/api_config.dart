class ApiConfig {
  static const baseUrl = String.fromEnvironment(
    'API_URL',
    defaultValue: 'http://192.168.1.4:8080/api', // émulateur Android
  );
}
