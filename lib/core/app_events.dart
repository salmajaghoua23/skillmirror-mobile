import 'package:flutter/foundation.dart';

/// Signaux entre écrans : le dashboard s'actualise quand un CV est confirmé.
class AppEvents {
  static final ValueNotifier<int> cvConfirmed = ValueNotifier<int>(0);
}