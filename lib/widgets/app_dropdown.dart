import 'package:flutter/material.dart';

class AppDropdown extends StatelessWidget {
  final String label;
  final String? value;
  final Map<String, String> items; // valeur enregistrée -> texte affiché
  final ValueChanged<String?> onChanged;
  final bool required;

  const AppDropdown({
    super.key,
    required this.label,
    required this.value,
    required this.items,
    required this.onChanged,
    this.required = true,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: DropdownButtonFormField<String>(
        initialValue: value,
        isExpanded: true,
        decoration: InputDecoration(
          labelText: required ? '$label *' : label,
          border: const OutlineInputBorder(),
        ),
        items: items.entries
            .map((e) => DropdownMenuItem(value: e.key, child: Text(e.value)))
            .toList(),
        onChanged: onChanged,
        validator: (v) => required && v == null ? 'Champ obligatoire' : null,
      ),
    );
  }
}
