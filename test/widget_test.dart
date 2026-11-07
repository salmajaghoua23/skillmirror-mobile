import 'package:flutter_test/flutter_test.dart';

import 'package:skill_mirror/main.dart';

void main() {
  testWidgets('Écran de choix du profil : affichage et sélection', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(const MyApp());

    // Éléments principaux de l'écran
    expect(find.text('SkillMirror'), findsOneWidget);
    expect(find.textContaining('Le bon profil'), findsOneWidget);
    expect(find.text('Chercheur d\'emploi'), findsOneWidget);
    expect(find.text('Recruteur'), findsOneWidget);
    expect(find.text('Continuer'), findsOneWidget);

    // Sélection d'un autre profil puis validation
    await tester.tap(find.text('Recruteur'));
    await tester.pump();

    await tester.tap(find.text('Continuer'));
    await tester.pump();

    expect(find.text('Profil choisi : Recruteur'), findsOneWidget);
  });
}
