#!/usr/bin/env python3
"""
Add a 'reference_unit' field to every entry in the jade_tipi_units.jsonl file.

The reference unit for each property is the SI coherent base/derived unit —
the unit whose conversion_factor is 1.0 (or closest to 1.0) and has NO
conversion_offset.

When multiple units share conversion_factor == 1.0 for the same property,
explicit overrides or heuristics are used to pick the correct SI coherent unit.
"""

import json
import sys
from collections import defaultdict, OrderedDict

JSONL_PATH = (
    "/Users/duncanscott/git-hub/duncanscott/jade-tipi/"
    "libraries/jade-tipi-dto/src/main/resources/units/jade_tipi_units.jsonl"
)

# Explicit overrides for properties where multiple units have factor=1.0
# and heuristics alone would be insufficient or risky.
REFERENCE_UNIT_OVERRIDES = {
    "angular momentum": "kilogram square meter per second",
    "catalytic activity": "katal",
    "catalytic activity concentration": "katal per cubic meter",
    "electrical conductance": "siemens",
    "energy": "joule",
    "heat capacity": "joule per kelvin",
    "heat transfer": "watt per square meter kelvin",
    "information": "byte",
    "information rate": "byte per second",
    "magnetic moment": "ampere square meter",
    "mass concentration": "kilogram per cubic meter",
    "molar concentration": "mole per cubic meter",
    "molar energy": "joule per mole",
    "radiant exposure": "joule per square meter",
    "reciprocal length": "reciprocal meter",
    "specific heat capacity": "joule per kilogram kelvin",
    "specific power": "watt per kilogram",
    "surface tension": "newton per meter",
    "temperature interval": "kelvin",
    "thermal conductance": "watt per kelvin",
    "thermal conductivity": "watt per meter kelvin",
    "volume": "cubic meter",
    "volume rate": "cubic meter per second",
    "volumetric number rate": "becquerel per cubic meter",
}


def find_reference_unit(entries_for_property):
    """Find the reference unit for a group of entries sharing the same property.

    The reference unit is the entry with conversion_factor closest to 1.0
    and NO conversion_offset.

    Returns the singular unit name of the reference unit.
    """
    prop = entries_for_property[0]["property"]

    # Check for explicit override first
    if prop in REFERENCE_UNIT_OVERRIDES:
        return REFERENCE_UNIT_OVERRIDES[prop]

    # Filter to candidates: no conversion_offset
    candidates = [
        e for e in entries_for_property if "conversion_offset" not in e
    ]

    if not candidates:
        # Fallback: all entries have offsets, pick closest to 1.0
        candidates = entries_for_property

    # Sort by distance from 1.0
    candidates.sort(key=lambda e: abs(e["conversion_factor"] - 1.0))

    return candidates[0]["unit"]


def insert_after_key(ordered_dict, after_key, new_key, new_value):
    """Insert new_key:new_value into an OrderedDict right after after_key."""
    items = list(ordered_dict.items())
    new_items = []
    for k, v in items:
        new_items.append((k, v))
        if k == after_key:
            new_items.append((new_key, new_value))
    return OrderedDict(new_items)


def main():
    # Read all entries
    with open(JSONL_PATH, "r") as f:
        entries = [json.loads(line.strip()) for line in f if line.strip()]

    # Group by property
    by_property = defaultdict(list)
    for entry in entries:
        by_property[entry["property"]].append(entry)

    # Determine reference unit for each property
    reference_units = {}
    for prop in sorted(by_property.keys()):
        ref = find_reference_unit(by_property[prop])
        reference_units[prop] = ref

    # Print each property and its reference unit
    print("Reference units by property:")
    print("-" * 70)
    for prop in sorted(reference_units.keys()):
        print(f"  {prop:45s} -> {reference_units[prop]}")
    print("-" * 70)
    print(f"Total properties: {len(reference_units)}")
    print()

    # Add reference_unit to every entry, placed after conversion_factor
    # (or after conversion_offset if present)
    updated_entries = []
    for entry in entries:
        ref_unit = reference_units[entry["property"]]
        od = OrderedDict(entry)

        # Determine insertion point: after conversion_offset if present,
        # otherwise after conversion_factor
        if "conversion_offset" in od:
            insert_after = "conversion_offset"
        else:
            insert_after = "conversion_factor"

        od = insert_after_key(od, insert_after, "reference_unit", ref_unit)
        updated_entries.append(od)

    # Write back
    with open(JSONL_PATH, "w") as f:
        for entry in updated_entries:
            f.write(json.dumps(entry, ensure_ascii=False) + "\n")

    print(f"Total entries updated: {len(updated_entries)}")
    print()

    # Spot-check specific entries
    spot_checks = ["liter", "degree Fahrenheit", "mile", "kilogram"]
    print("Spot checks:")
    print("-" * 70)
    for entry in updated_entries:
        if entry["unit"] in spot_checks:
            print(json.dumps(entry, ensure_ascii=False, indent=2))
            print()


if __name__ == "__main__":
    main()
