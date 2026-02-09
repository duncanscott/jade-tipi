#!/usr/bin/env python3
"""
Merge jade_tipi_si_units.jsonl and jade_tipi_units.jsonl into units_of_measurement.jsonl.

Unit data partially derived from the uom crate (https://github.com/iliekturtles/uom)
Copyright (c) 2019 Mike Boutin, licensed under MIT / Apache-2.0.
See THIRD-PARTY-LICENSES in the project root for the full license text.
"""

import json
import math
import os
from collections import OrderedDict

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
SI_FILE = os.path.join(SCRIPT_DIR, "jade_tipi_si_units.jsonl")
UOM_FILE = os.path.join(SCRIPT_DIR, "jade_tipi_units.jsonl")
OUTPUT_FILE = os.path.join(SCRIPT_DIR, "units_of_measurement.jsonl")

# ---------- SI prefix multipliers ----------
PREFIX_MULTIPLIERS = {
    "quetta": 1e30, "ronna": 1e27, "yotta": 1e24, "zetta": 1e21, "exa": 1e18,
    "peta": 1e15, "tera": 1e12, "giga": 1e9, "mega": 1e6, "kilo": 1e3,
    "hecto": 1e2, "deca": 1e1, None: 1.0, "deci": 1e-1, "centi": 1e-2,
    "milli": 1e-3, "micro": 1e-6, "nano": 1e-9, "pico": 1e-12, "femto": 1e-15,
    "atto": 1e-18, "zepto": 1e-21, "yocto": 1e-24, "ronto": 1e-27, "quecto": 1e-30,
}

# ---------- Property name mapping: SI -> UOM ----------
# SI properties that differ from UOM naming
SI_TO_UOM_PROPERTY = {
    "electric resistance": "electrical resistance",
    "electric conductance": "electrical conductance",
    "electric potential difference": "electric potential",
    "plane angle": "angle",
    "absorbed dose": "absorbed dose",       # not in UOM - will need special handling
    "dose equivalent": "dose equivalent",   # not in UOM - will need special handling
    "luminous flux": "luminous flux",       # not in UOM - will need special handling
    "logarithmic ratio": "logarithmic ratio",  # not in UOM
    "temperature": "thermodynamic temperature",  # degree Celsius -> thermodynamic temperature
}

# Reverse mapping: UOM -> SI (for matching)
UOM_TO_SI_PROPERTY = {v: k for k, v in SI_TO_UOM_PROPERTY.items() if k != v}

# ---------- Special plural forms ----------
SPECIAL_PLURALS = {
    "degree Celsius": "degrees Celsius",
    "hertz": "hertz",
    "lux": "lux",
    "siemens": "siemens",
}


def make_plural(unit_name):
    """Generate plural form of a unit name."""
    if unit_name in SPECIAL_PLURALS:
        return SPECIAL_PLURALS[unit_name]
    # Check for units ending in s, x, or z
    if unit_name.endswith("s") or unit_name.endswith("x") or unit_name.endswith("z"):
        return unit_name
    return unit_name + "s"


def construct_full_name(si_entry):
    """Construct the full unit name from an SI entry."""
    prefix = si_entry.get("prefix")
    unit = si_entry["unit"]
    if prefix is None:
        return unit
    return prefix + unit


def format_conversion_factor(value):
    """Format conversion factor for clean JSON output."""
    if value == 0.0:
        return 0.0
    # For exact integer values
    if value == int(value) and abs(value) < 1e16:
        return float(int(value))
    return value


def build_ordered_entry(unit, prefix, symbol, plural, prop, conversion_factor,
                        conversion_offset, reference_unit, alternate_unit, system):
    """Build an OrderedDict with fields in the required order."""
    entry = OrderedDict()
    entry["unit"] = unit
    entry["prefix"] = prefix
    entry["symbol"] = symbol
    entry["plural"] = plural
    entry["property"] = prop
    entry["conversion_factor"] = conversion_factor
    if conversion_offset is not None:
        entry["conversion_offset"] = conversion_offset
    entry["reference_unit"] = reference_unit
    if alternate_unit is not None:
        entry["alternate_unit"] = alternate_unit
    entry["system"] = system
    return entry


def load_jsonl(filepath):
    """Load a JSONL file into a list of dicts."""
    entries = []
    with open(filepath, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                entries.append(json.loads(line))
    return entries


def main():
    # Load both files
    si_entries = load_jsonl(SI_FILE)
    uom_entries = load_jsonl(UOM_FILE)

    print(f"Loaded {len(si_entries)} SI entries")
    print(f"Loaded {len(uom_entries)} UOM entries")

    # ---------- Build UOM lookup: (unit_name, property) -> list of entries ----------
    # Some units appear multiple times (e.g., degree Celsius in both temperature interval
    # and thermodynamic temperature)
    uom_by_unit_prop = {}
    for entry in uom_entries:
        key = (entry["unit"], entry["property"])
        uom_by_unit_prop.setdefault(key, []).append(entry)

    # Also build UOM lookup by just unit name for reference unit discovery
    uom_by_unit = {}
    for entry in uom_entries:
        uom_by_unit.setdefault(entry["unit"], []).append(entry)

    # ---------- Build reference info from UOM by property ----------
    # For each property in UOM, find reference_unit
    uom_ref_by_property = {}
    for entry in uom_entries:
        prop = entry["property"]
        if prop not in uom_ref_by_property:
            uom_ref_by_property[prop] = entry.get("reference_unit")

    # ---------- Build SI lookup for matching ----------
    # Group SI entries by base_unit and property to find matched variants
    si_by_base_prop = {}
    for entry in si_entries:
        key = (entry["unit"], entry["property"])
        si_by_base_prop.setdefault(key, []).append(entry)

    # ---------- Process matches ----------
    # Track which UOM entries got matched (by index)
    uom_matched = set()
    # Track which SI entries got matched
    si_matched = set()
    # Output entries
    output_entries = []

    # For each SI entry, try to match to UOM
    for si_idx, si_entry in enumerate(si_entries):
        full_name = construct_full_name(si_entry)
        si_prop = si_entry["property"]

        # Determine the UOM property name(s) to look for
        uom_prop_candidates = [si_prop]
        if si_prop in SI_TO_UOM_PROPERTY:
            uom_prop_candidates.append(SI_TO_UOM_PROPERTY[si_prop])

        matched = False
        for uom_prop in uom_prop_candidates:
            key = (full_name, uom_prop)
            if key in uom_by_unit_prop:
                for uom_entry in uom_by_unit_prop[key]:
                    # Found a match - enrich UOM entry with SI fields
                    uom_idx = uom_entries.index(uom_entry)
                    uom_matched.add(uom_idx)
                    si_matched.add(si_idx)

                    enriched = build_ordered_entry(
                        unit=uom_entry["unit"],
                        prefix=si_entry.get("prefix"),
                        symbol=uom_entry["symbol"],
                        plural=uom_entry["plural"],
                        prop=uom_entry["property"],
                        conversion_factor=uom_entry["conversion_factor"],
                        conversion_offset=uom_entry.get("conversion_offset"),
                        reference_unit=uom_entry.get("reference_unit"),
                        alternate_unit=si_entry.get("alternate_unit"),
                        system=uom_entry["system"],
                    )
                    output_entries.append(enriched)
                matched = True
                break  # Don't look at more property candidates

        if not matched:
            si_matched.add(si_idx)  # Still mark as processed

    # Count actual matches (SI entries that found UOM counterparts)
    matched_count = len(uom_matched)

    # ---------- Handle unmatched SI entries ----------
    # We need to compute conversion factors for SI entries not in UOM

    # First, build a map of base unit conversion info from UOM matches
    # For each SI base unit + property, find what the UOM conversion factor is
    # for the matched entries, so we can compute for unmatched ones.
    #
    # Strategy: for a given base unit (e.g., "gram") with property "mass",
    # find a matched UOM entry (e.g., "gram" with conversion_factor 0.001 to "kilogram")
    # and use that to compute conversion factors for all prefixed variants.

    # Build base_unit_info: (si_base_unit, si_property) -> {uom_property, reference_unit, base_cf}
    # where base_cf is the conversion factor for the null-prefix version
    base_unit_info = {}

    for si_entry in si_entries:
        full_name = construct_full_name(si_entry)
        si_prop = si_entry["property"]
        base_unit = si_entry["unit"]
        prefix = si_entry.get("prefix")

        # Try all property name candidates
        uom_prop_candidates = [si_prop]
        if si_prop in SI_TO_UOM_PROPERTY:
            uom_prop_candidates.append(SI_TO_UOM_PROPERTY[si_prop])

        for uom_prop in uom_prop_candidates:
            key = (full_name, uom_prop)
            if key in uom_by_unit_prop:
                uom_entry = uom_by_unit_prop[key][0]
                cf = uom_entry["conversion_factor"]
                prefix_mult = PREFIX_MULTIPLIERS.get(prefix, 1.0)
                # base_cf = cf / prefix_mult (this is what the null-prefix unit would have)
                null_prefix_cf = cf / prefix_mult
                info_key = (base_unit, si_prop)
                if info_key not in base_unit_info:
                    base_unit_info[info_key] = {
                        "uom_property": uom_prop,
                        "reference_unit": uom_entry.get("reference_unit"),
                        "base_cf": null_prefix_cf,
                    }
                break

    # Special hardcoded conversion info for units with no UOM match at all
    SPECIAL_CONVERSIONS = {
        ("arcminute", "plane angle"): {
            "conversion_factor": 0.0002908882086657216,  # pi/10800
            "reference_unit": "radian",
            "uom_property": "angle",
        },
        ("arcsecond", "plane angle"): {
            "conversion_factor": 4.84813681109536e-06,  # pi/648000
            "reference_unit": "radian",
            "uom_property": "angle",
        },
        ("bel", "logarithmic ratio"): {
            "conversion_factor": 1.0,
            "reference_unit": "bel",
            "uom_property": "logarithmic ratio",
        },
        ("neper", "logarithmic ratio"): {
            "conversion_factor": 1.0,
            "reference_unit": "neper",
            "uom_property": "logarithmic ratio",
        },
        ("degree", "plane angle"): {
            "conversion_factor": 0.017453292519943295,  # pi/180
            "reference_unit": "radian",
            "uom_property": "angle",
        },
        ("minute", "time"): {
            "conversion_factor": 60.0,
            "reference_unit": "second",
            "uom_property": "time",
        },
        ("hour", "time"): {
            "conversion_factor": 3600.0,
            "reference_unit": "second",
            "uom_property": "time",
        },
        ("day", "time"): {
            "conversion_factor": 86400.0,
            "reference_unit": "second",
            "uom_property": "time",
        },
        ("degree Celsius", "temperature"): {
            "conversion_factor": 1.0,
            "conversion_offset": 273.15,
            "reference_unit": "kelvin",
            "uom_property": "thermodynamic temperature",
        },
        # Gray: SI unit of absorbed dose (J/kg). Base unit is gray itself.
        ("gray", "absorbed dose"): {
            "conversion_factor": 1.0,
            "reference_unit": "gray",
            "uom_property": "absorbed dose",
        },
        # Sievert: SI unit of dose equivalent (J/kg). Base unit is sievert itself.
        ("sievert", "dose equivalent"): {
            "conversion_factor": 1.0,
            "reference_unit": "sievert",
            "uom_property": "dose equivalent",
        },
        # Lumen: SI unit of luminous flux (cd*sr). Base unit is lumen itself.
        ("lumen", "luminous flux"): {
            "conversion_factor": 1.0,
            "reference_unit": "lumen",
            "uom_property": "luminous flux",
        },
        # Tonne: 1 tonne = 1000 kg. Reference unit is kilogram.
        ("tonne", "mass"): {
            "conversion_factor": 1000.0,
            "reference_unit": "kilogram",
            "uom_property": "mass",
        },
    }

    # Now generate entries for unmatched SI entries
    unmatched_si_entries = []
    errors = []

    for si_idx, si_entry in enumerate(si_entries):
        full_name = construct_full_name(si_entry)
        si_prop = si_entry["property"]
        base_unit = si_entry["unit"]
        prefix = si_entry.get("prefix")

        # Check if this was already matched to a UOM entry
        uom_prop_candidates = [si_prop]
        if si_prop in SI_TO_UOM_PROPERTY:
            uom_prop_candidates.append(SI_TO_UOM_PROPERTY[si_prop])

        already_matched = False
        for uom_prop in uom_prop_candidates:
            key = (full_name, uom_prop)
            if key in uom_by_unit_prop:
                already_matched = True
                break

        if already_matched:
            continue

        # This SI entry is unmatched - need to create a new entry
        conversion_factor = None
        reference_unit = None
        conversion_offset = None
        output_property = si_prop  # default to SI property name

        # Check special conversions first (for the specific full_name)
        special_key = (full_name, si_prop)
        if special_key in SPECIAL_CONVERSIONS:
            spec = SPECIAL_CONVERSIONS[special_key]
            conversion_factor = spec["conversion_factor"]
            reference_unit = spec["reference_unit"]
            conversion_offset = spec.get("conversion_offset")
            output_property = spec["uom_property"]
        else:
            # Check if the base unit (no prefix) has special conversion
            base_key = (base_unit, si_prop)
            if base_key in SPECIAL_CONVERSIONS:
                spec = SPECIAL_CONVERSIONS[base_key]
                base_cf = spec["conversion_factor"]
                reference_unit = spec["reference_unit"]
                output_property = spec["uom_property"]
                # Apply prefix multiplier relative to null-prefix base
                prefix_mult = PREFIX_MULTIPLIERS.get(prefix, 1.0)
                null_mult = PREFIX_MULTIPLIERS[None]
                conversion_factor = base_cf * (prefix_mult / null_mult)
            elif base_key in base_unit_info:
                info = base_unit_info[base_key]
                base_cf = info["base_cf"]
                reference_unit = info["reference_unit"]
                output_property = info["uom_property"]
                # Conversion factor = base_cf * prefix_multiplier
                prefix_mult = PREFIX_MULTIPLIERS.get(prefix, 1.0)
                conversion_factor = base_cf * prefix_mult
            else:
                # Try with mapped property
                for alt_prop in uom_prop_candidates:
                    alt_key = (base_unit, alt_prop) if alt_prop != si_prop else None
                    if alt_key and alt_key in base_unit_info:
                        info = base_unit_info[alt_key]
                        base_cf = info["base_cf"]
                        reference_unit = info["reference_unit"]
                        output_property = info["uom_property"]
                        prefix_mult = PREFIX_MULTIPLIERS.get(prefix, 1.0)
                        conversion_factor = base_cf * prefix_mult
                        break

        if conversion_factor is None:
            errors.append(f"Could not compute conversion for: {full_name} ({si_prop})")
            # Still create entry with null conversion
            conversion_factor = None
            reference_unit = None
            # Use mapped property if available
            if si_prop in SI_TO_UOM_PROPERTY:
                output_property = SI_TO_UOM_PROPERTY[si_prop]

        plural = make_plural(full_name)

        new_entry = build_ordered_entry(
            unit=full_name,
            prefix=prefix,
            symbol=si_entry["symbol"],
            plural=plural,
            prop=output_property,
            conversion_factor=conversion_factor,
            conversion_offset=conversion_offset,
            reference_unit=reference_unit,
            alternate_unit=si_entry.get("alternate_unit"),
            system=si_entry["system"],
        )
        unmatched_si_entries.append(new_entry)

    # ---------- Handle unmatched UOM entries ----------
    unmatched_uom_entries = []
    for uom_idx, uom_entry in enumerate(uom_entries):
        if uom_idx not in uom_matched:
            entry = build_ordered_entry(
                unit=uom_entry["unit"],
                prefix=None,
                symbol=uom_entry["symbol"],
                plural=uom_entry["plural"],
                prop=uom_entry["property"],
                conversion_factor=uom_entry["conversion_factor"],
                conversion_offset=uom_entry.get("conversion_offset"),
                reference_unit=uom_entry.get("reference_unit"),
                alternate_unit=None,
                system=uom_entry["system"],
            )
            unmatched_uom_entries.append(entry)

    # ---------- Combine all entries ----------
    all_entries = output_entries + unmatched_si_entries + unmatched_uom_entries

    # ---------- Sort by property then unit ----------
    all_entries.sort(key=lambda e: (e["property"], e["unit"]))

    # ---------- Write output ----------
    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        for entry in all_entries:
            f.write(json.dumps(entry, ensure_ascii=False) + "\n")

    # ---------- Report ----------
    print(f"\n=== MERGE REPORT ===")
    print(f"Total entries in output: {len(all_entries)}")
    print(f"Matched between files: {matched_count}")
    print(f"Unmatched SI entries (new): {len(unmatched_si_entries)}")
    print(f"Unmatched UOM entries (kept as-is): {len(unmatched_uom_entries)}")

    if errors:
        print(f"\nEntries that couldn't be fully processed ({len(errors)}):")
        for e in errors:
            print(f"  - {e}")
    else:
        print(f"\nAll entries processed successfully.")

    # ---------- Spot checks ----------
    spot_check = ["liter", "milliliter", "kilogram", "gram", "degree Fahrenheit",
                  "arcminute", "neper", "decibel"]
    print(f"\n=== SPOT CHECKS ===")
    for name in spot_check:
        found = [e for e in all_entries if e["unit"] == name]
        if found:
            for e in found:
                print(f"\n{name} ({e['property']}):")
                print(f"  {json.dumps(e, ensure_ascii=False)}")
        else:
            print(f"\n{name}: NOT FOUND")


if __name__ == "__main__":
    main()
