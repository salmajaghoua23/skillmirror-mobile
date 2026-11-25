class ApiConfig {
  static const baseUrl = String.fromEnvironment(
    'API_URL',
    defaultValue: 'http://192.168.11.231:8080/api', // émulateur Android
  );

}
