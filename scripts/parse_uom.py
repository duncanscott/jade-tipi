#!/usr/bin/env python3
"""
Parse Rust UOM (Units of Measurement) .rs files and output a JSONL file
with unit definitions including conversion factors and system classification.

Unit data derived from the uom crate (https://github.com/iliekturtles/uom)
Copyright (c) 2019 Mike Boutin, licensed under MIT / Apache-2.0.
See THIRD-PARTY-LICENSES in the project root for the full license text.
"""

import json
import os
import re
import sys
from pathlib import Path

# Directories
SI_DIR = Path("/Users/duncanscott/git-hub/iliekturtles/uom/src/si")
OUTPUT_FILE = Path("/Users/duncanscott/git-hub/duncanscott/jade-tipi/libraries/jade-tipi-dto/src/main/resources/units/jade_tipi_units.jsonl")

# Files to exclude
EXCLUDED_FILES = {"mod.rs", "prefix.rs"}

# SI prefix values
PREFIX_VALUES = {
    "yotta": 1.0e24,
    "zetta": 1.0e21,
    "exa": 1.0e18,
    "peta": 1.0e15,
    "tera": 1.0e12,
    "giga": 1.0e9,
    "mega": 1.0e6,
    "kilo": 1.0e3,
    "hecto": 1.0e2,
    "deca": 1.0e1,
    "none": 1.0,
    "deci": 1.0e-1,
    "centi": 1.0e-2,
    "milli": 1.0e-3,
    "micro": 1.0e-6,
    "nano": 1.0e-9,
    "pico": 1.0e-12,
    "femto": 1.0e-15,
    "atto": 1.0e-18,
    "zepto": 1.0e-21,
    "yocto": 1.0e-24,
    # IEC binary prefixes
    "yobi": 1024 ** 8,
    "zebi": 1024 ** 7,
    "exbi": 1024 ** 6,
    "pebi": 1024 ** 5,
    "tebi": 1024 ** 4,
    "gibi": 1024 ** 3,
    "mebi": 1024 ** 2,
    "kibi": 1024,
}

# Known IEC binary prefix names (for system classification)
IEC_PREFIXES = {"yobi", "zebi", "exbi", "pebi", "tebi", "gibi", "mebi", "kibi"}

# SI prefix names (for system classification)
SI_PREFIXES = {
    "yotta", "zetta", "exa", "peta", "tera", "giga", "mega", "kilo",
    "hecto", "deca", "none", "deci", "centi", "milli", "micro",
    "nano", "pico", "femto", "atto", "zepto", "yocto",
}

# Known unit identifiers -> system classification
IMPERIAL_US_UNITS = {
    "foot", "foot_survey", "inch", "yard", "mile", "mile_survey", "microinch", "mil",
    "pound", "pound_troy", "ounce", "ounce_troy", "grain", "pennyweight",
    "hundredweight_long", "hundredweight_short", "ton_long", "ton_short", "slug",
    "ton_assay",
    "gallon", "pint_liquid", "quart_liquid", "cup", "tablespoon", "teaspoon",
    "fluid_ounce", "barrel", "bushel", "cord", "peck", "pint_dry", "quart_dry",
    "register_ton",
    "acre", "acre_foot",
    "fathom", "chain", "rod", "data_mile",
    "foot_pound", "foot_pound_force", "foot_poundal",
    "poundal", "pound_force", "ounce_force", "kip", "ton_force",
    "horsepower", "horsepower_imperial", "horsepower_boiler", "horsepower_electric",
    "hydraulic_horsepower",
    "foot_pound_per_hour", "foot_pound_per_minute", "foot_pound_per_second",
    "erg_per_second",
    "btu", "btu_it", "btu_39", "btu_59", "btu_60",
    "therm_ec", "therm_us",
    "degree_fahrenheit", "degree_rankine",
    "cubic_foot", "cubic_inch", "cubic_mile", "cubic_yard",
    "square_foot", "square_inch", "square_mile", "square_yard",
    "circular_mil",
    "gallon_imperial", "fluid_ounce_imperial", "gill_imperial", "gill",
    "foot_of_mercury", "foot_of_water", "foot_of_water_39_2",
    "inch_of_mercury", "inch_of_mercury_32", "inch_of_mercury_60",
    "inch_of_water", "inch_of_water_39_2", "inch_of_water_60",
    "pound_force_per_square_foot", "pound_force_per_square_inch",
    "poundal_per_square_foot", "kip_per_square_inch", "psi",
    "foot_per_hour", "foot_per_minute", "foot_per_second",
    "inch_per_second", "inch_per_minute",
    "mile_per_hour", "mile_per_minute", "mile_per_second",
    "pica_computer", "pica_printers", "point_computer", "point_printers",
}

CGS_UNITS = {
    "dyne", "erg",
    "statampere", "statvolt", "abampere", "abvolt", "gilbert",
    "dyne_per_square_centimeter",
}

METRIC_NON_SI_UNITS = {
    "bar", "atmosphere", "atmosphere_technical", "torr", "millitorr",
    "calorie", "calorie_it", "calorie_15", "calorie_20",
    "calorie_nutrition", "calorie_it_nutrition",
    "kilocalorie", "kilocalorie_it",
    "kilogram_force", "gram_force",
    "kilogram_force_per_square_meter",
    "kilogram_force_per_square_centimeter",
    "gram_force_per_square_centimeter",
    "kilogram_force_per_square_millimeter",
    "carat",
    "ton",
    "hectare", "are",
    "liter",
    "stere",
    "millimeter_of_mercury", "centimeter_of_mercury", "centimeter_of_mercury_0",
    "centimeter_of_water", "centimeter_of_water_4",
    "millimeter_of_water",
    "millibar",
    "newton_per_square_millimeter",
    "horsepower_metric",
    "micron",
    "angstrom", "fermi",
    "ton_tnt",
    "barn",
    "watt_second", "watt_hour",
    "kilowatt_hour", "megawatt_hour", "gigawatt_hour", "terawatt_hour", "petawatt_hour",
    "hectowatt_hour", "decawatt_hour", "milliwatt_hour", "microwatt_hour",
    "kilometer_per_hour", "millimeter_per_minute",
    "degree_celsius",
}

ATOMIC_NATURAL_UNITS = {
    "bohr_radius", "dalton", "electronvolt", "hartree",
    "elementary_charge_per_second",
    "petaelectronvolt", "teraelectronvolt", "gigaelectronvolt",
    "megaelectronvolt", "kiloelectronvolt", "hectoelectronvolt", "decaelectronvolt",
}

NAUTICAL_UNITS = {
    "nautical_mile", "knot",
}

ASTRONOMICAL_UNITS = {
    "astronomical_unit", "light_year", "parsec",
}

INFORMATION_UNITS = {
    "bit", "byte", "octet", "nibble", "crumb", "shannon",
    "natural_unit_of_information", "trit", "deciban", "hartley",
}

# Ancient Roman unit identifiers (detected by comment context in the files)
ANCIENT_ROMAN_UNITS = {
    # Length
    "leuga", "mille_passus", "stadium", "actus", "decempeda", "passus",
    "gradus", "cubitum", "palmipes", "pes", "palmus_maior", "palmus",
    "uncia", "digitus",
    # Mass
    "libra", "deunx", "dextans", "dodrans", "bes", "septunx", "semis",
    "quincunx", "triens", "quadrans_teruncius", "sextans", "sescuncia",
    "semuncia", "duella", "sicilicus", "sextula", "semisextula",
    "scrupulum", "obolus", "siliqua",
    # Volume
    "culeus", "amphora_quadrantal", "urna", "modius_castrensis", "modius",
    "semimodius", "congius", "sextarius", "hemina", "quartarius",
    "acetabulum", "cyathus", "ligula",
    # Area
    "saltus", "centuria", "heredium", "jugerum", "actus_quadratus",
    "clima", "semiuncia", "actus_simplex", "duo_scrupulum",
    "dimidium_scrupulum", "pes_quadratus", "quadrans",
}

# Note: Some identifiers overlap across different properties (e.g., "uncia" in both length and mass).
# The Roman detection is done per-file using comment context, not just by identifier name.


def clean_rust_number(s):
    """Remove Rust underscores from number literals and handle Rust float syntax."""
    s = s.strip()
    # Remove underscores used as digit separators
    s = s.replace("_", "")
    return s


def resolve_prefix(name):
    """Resolve a prefix!() macro to its numeric value."""
    if name in PREFIX_VALUES:
        return PREFIX_VALUES[name]
    raise ValueError(f"Unknown prefix: {name}")


def evaluate_conversion_expr(expr_str):
    """
    Evaluate a conversion expression that may contain prefix!() macros,
    arithmetic operators, and literal floats.
    """
    expr = expr_str.strip()

    # Replace all prefix!(name) with their numeric values
    def replace_prefix(m):
        name = m.group(1)
        val = resolve_prefix(name)
        return repr(val)

    expr = re.sub(r'prefix!\((\w+)\)', replace_prefix, expr)

    # Clean Rust number format (underscores, E notation)
    # We need to handle tokens like 1.609_344_E3 -> 1.609344E3
    # But after prefix replacement, we might have repr() floats too
    # Process numeric tokens that still have underscores
    def clean_number_token(m):
        return m.group(0).replace("_", "")

    expr = re.sub(r'[\d._]+[eE][+-]?\d+', clean_number_token, expr)
    expr = re.sub(r'[\d._]+\.[\d_]*', clean_number_token, expr)

    # Now evaluate safely
    try:
        result = eval(expr)
        return float(result)
    except Exception as e:
        raise ValueError(f"Cannot evaluate expression '{expr_str}' -> '{expr}': {e}")


def extract_prefixes_used(expr_str):
    """Extract the prefix names used in a conversion expression."""
    return set(re.findall(r'prefix!\((\w+)\)', expr_str))


def classify_system(identifier, expr_str, prefixes_used, property_name, in_roman_section):
    """Classify the unit system based on various heuristics."""

    # Check Ancient Roman first (based on comment context)
    if in_roman_section:
        return "Ancient Roman"

    # Check for IEC binary prefixes
    if prefixes_used & IEC_PREFIXES:
        return "IEC"

    # Check known unit sets (using identifier)
    if identifier in IMPERIAL_US_UNITS:
        return "Imperial"
    if identifier in CGS_UNITS:
        return "CGS"
    if identifier in NAUTICAL_UNITS:
        return "Nautical"
    if identifier in ASTRONOMICAL_UNITS:
        return "Astronomical"

    # For information property, check specific units
    if property_name == "information":
        if identifier in INFORMATION_UNITS:
            return "Information"

    # Atomic/Natural units - check identifier patterns
    if identifier in ATOMIC_NATURAL_UNITS:
        return "Atomic/Natural"
    if identifier.startswith("atomic_unit_of_"):
        return "Atomic/Natural"
    if identifier.startswith("natural_unit_of_") and property_name != "information":
        return "Atomic/Natural"

    # Sidereal/tropical time units
    if identifier.endswith("_sidereal") or identifier.endswith("_tropical"):
        return "Astronomical"

    if identifier in METRIC_NON_SI_UNITS:
        return "Metric"

    # If conversion expression uses only SI prefix!() macros (possibly with /prefix!(kilo) for mass base unit)
    # and no literal numbers other than division factors, it's SI
    if prefixes_used:
        non_si_prefixes = prefixes_used - SI_PREFIXES
        if not non_si_prefixes:
            # Check if the expression also has non-prefix numeric literals
            expr_no_prefix = re.sub(r'prefix!\(\w+\)', '', expr_str).strip()
            # Remove operators and whitespace
            expr_no_prefix = re.sub(r'[*/+\-\s]', '', expr_no_prefix)
            if not expr_no_prefix or expr_no_prefix in ('8.0', '2.0', '4.0'):
                # Pure SI prefix expression (with possible /8.0 for bits)
                return "SI"

    # If no prefixes used and it's a literal number, check known units
    if not prefixes_used:
        # Check various patterns
        if identifier.startswith("speed_of_light"):
            return "Atomic/Natural"
        if identifier == "quad":
            return "Imperial"
        if identifier == "shake":
            return "other"
        if identifier in ("day", "hour", "minute", "year"):
            return "other"
        if identifier == "radian" or identifier == "revolution":
            return "SI"
        if identifier in ("degree", "gon", "mil", "second") and property_name == "angle":
            return "other"

    return "other"


def parse_rs_file(filepath):
    """Parse a single .rs file and extract unit definitions."""
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # Extract property name from quantity line
    # Pattern: quantity: TypeName; "property_name";
    quantity_match = re.search(r'quantity:\s+\w+;\s*"([^"]+)"', content)
    if not quantity_match:
        return None, []

    property_name = quantity_match.group(1)

    # Extract the units { ... } block
    # Find "units {" and then match to its closing "}"
    units_match = re.search(r'units\s*\{(.*?)^\s*\}', content, re.DOTALL | re.MULTILINE)
    if not units_match:
        return property_name, []

    units_block = units_match.group(1)

    # Parse unit entries from the units block
    units = []
    errors = []

    # Track whether we're in an "Ancient Roman" section
    in_roman_section = False

    # Split into lines for processing
    lines = units_block.split('\n')

    # We'll accumulate text and process entries
    # An entry starts with @identifier and ends with the last semicolon (after the strings)
    current_entry = ""
    i = 0

    while i < len(lines):
        line = lines[i]
        stripped = line.strip()

        # Check for Roman section comment
        if re.match(r'^//+\s*[Aa]ncient\s+[Rr]oman', stripped):
            in_roman_section = True
            i += 1
            continue

        # Skip comment-only lines (but not mixed lines that have code + comment)
        if stripped.startswith('//'):
            i += 1
            continue

        # Skip empty lines
        if not stripped:
            i += 1
            continue

        # Remove inline comments (be careful not to remove strings)
        # Simple approach: remove // that's not inside a string
        code_line = remove_inline_comment(stripped)

        if '@' in code_line and not current_entry:
            # Start of a new entry
            current_entry = code_line
        elif current_entry:
            # Continuation of previous entry
            current_entry += " " + code_line
        elif code_line:
            # Possibly a continuation from a previous line (strings on next line)
            # This shouldn't normally happen with our logic, but handle gracefully
            current_entry = code_line

        # Check if entry is complete (has the pattern: "...", "...", "...";)
        # We need at least 3 quoted strings followed by a semicolon
        if current_entry and is_entry_complete(current_entry):
            result = parse_unit_entry(current_entry, property_name, in_roman_section)
            if result:
                units.append(result)
            elif current_entry.strip():
                errors.append(f"  Failed to parse entry in {filepath.name}: {current_entry[:100]}")
            current_entry = ""

        i += 1

    return property_name, units, errors


def remove_inline_comment(line):
    """Remove inline comments, being careful about strings."""
    # Find // that's not inside a quoted string
    in_string = False
    i = 0
    while i < len(line):
        if line[i] == '"':
            in_string = not in_string
        elif not in_string and i + 1 < len(line) and line[i:i+2] == '//':
            return line[:i].strip()
        i += 1
    return line


def is_entry_complete(entry):
    """Check if an entry has all required parts (3 quoted strings ending with semicolon)."""
    # Count the number of quoted strings
    strings = re.findall(r'"[^"]*"', entry)
    # An entry is complete when it has at least 3 quoted strings and ends with ;
    return len(strings) >= 3 and entry.rstrip().endswith(';')


def parse_unit_entry(entry, property_name, in_roman_section):
    """
    Parse a single unit entry like:
    @identifier: conversion_expr; "symbol", "singular", "plural";
    or
    @identifier: conversion_expr, offset_expr; "symbol", "singular", "plural";
    """
    # Pattern: @identifier: <expr_part>; "symbol", "singular", "plural";
    # The expr_part may contain a comma for offset (temperature)

    # Extract identifier
    id_match = re.match(r'@(\w+)\s*:', entry)
    if not id_match:
        return None

    identifier = id_match.group(1)
    rest = entry[id_match.end():].strip()

    # Split on the FIRST semicolon to get the expression part and the strings part
    first_semi = find_first_semicolon_outside_strings(rest)
    if first_semi < 0:
        return None

    expr_part = rest[:first_semi].strip()
    strings_part = rest[first_semi + 1:].strip()

    # Extract the three quoted strings from strings_part
    string_matches = re.findall(r'"([^"]*)"', strings_part)
    if len(string_matches) < 3:
        return None

    symbol = string_matches[0]
    singular = string_matches[1]
    plural = string_matches[2]

    # Parse the expression part for conversion factor and optional offset
    # Check if there's a comma separating conversion and offset
    # But be careful: prefix!(yotta) / prefix!(kilo) contains no comma
    # Temperature offset: 1.0_E0, 273.15_E0
    # We need to distinguish between commas in the expression vs offset separator
    # The offset comma is the one that separates two complete expressions

    conversion_factor = None
    conversion_offset = None

    # Try to split on comma for offset (only if not inside prefix!())
    parts = split_on_offset_comma(expr_part)

    if len(parts) == 2:
        # Has offset
        try:
            conversion_factor = evaluate_conversion_expr(parts[0])
            conversion_offset = evaluate_conversion_expr(parts[1])
        except ValueError as e:
            print(f"  Warning: {e}", file=sys.stderr)
            return None
    elif len(parts) == 1:
        try:
            conversion_factor = evaluate_conversion_expr(parts[0])
        except ValueError as e:
            print(f"  Warning: {e}", file=sys.stderr)
            return None
    else:
        print(f"  Warning: unexpected number of parts in expression: {expr_part}", file=sys.stderr)
        return None

    # Determine system classification
    prefixes_used = extract_prefixes_used(expr_part)
    system = classify_system(identifier, expr_part, prefixes_used, property_name, in_roman_section)

    result = {
        "unit": singular,
        "symbol": symbol,
        "plural": plural,
        "property": property_name,
        "conversion_factor": conversion_factor,
        "system": system,
    }

    if conversion_offset is not None:
        result["conversion_offset"] = conversion_offset

    return result


def find_first_semicolon_outside_strings(s):
    """Find the index of the first semicolon that's not inside a quoted string."""
    in_string = False
    paren_depth = 0
    for i, c in enumerate(s):
        if c == '"':
            in_string = not in_string
        elif not in_string:
            if c == '(':
                paren_depth += 1
            elif c == ')':
                paren_depth -= 1
            elif c == ';' and paren_depth == 0:
                return i
    return -1


def split_on_offset_comma(expr_part):
    """
    Split an expression on the comma that separates conversion factor from offset.
    Be careful not to split on commas inside parentheses (e.g., prefix!(name)).

    The offset comma pattern is: after the conversion expression is complete,
    there's a comma followed by the offset value.

    This is tricky because expressions like `prefix!(yotta) / prefix!(kilo)` don't have
    an offset comma, but `1.0_E0, 273.15_E0` does.
    """
    paren_depth = 0
    for i, c in enumerate(expr_part):
        if c == '(':
            paren_depth += 1
        elif c == ')':
            paren_depth -= 1
        elif c == ',' and paren_depth == 0:
            # This is a top-level comma -- likely the offset separator
            return [expr_part[:i].strip(), expr_part[i+1:].strip()]
    return [expr_part]


def main():
    all_units = []
    all_errors = []
    property_counts = {}

    # Get all .rs files except excluded ones
    rs_files = sorted([
        f for f in SI_DIR.glob("*.rs")
        if f.name not in EXCLUDED_FILES
    ])

    print(f"Found {len(rs_files)} .rs files to parse (excluding {EXCLUDED_FILES})")
    print()

    for filepath in rs_files:
        result = parse_rs_file(filepath)
        if result is None:
            continue

        if len(result) == 3:
            property_name, units, errors = result
        else:
            property_name, units = result
            errors = []

        if errors:
            all_errors.extend(errors)

        if units:
            property_counts[property_name] = len(units)
            all_units.extend(units)

    # Sort by property, then by unit name
    all_units.sort(key=lambda u: (u["property"], u["unit"]))

    # Ensure output directory exists
    OUTPUT_FILE.parent.mkdir(parents=True, exist_ok=True)

    # Write JSONL
    with open(OUTPUT_FILE, 'w', encoding='utf-8') as f:
        for unit in all_units:
            f.write(json.dumps(unit, ensure_ascii=False) + '\n')

    print(f"Output written to: {OUTPUT_FILE}")
    print(f"Total units: {len(all_units)}")
    print()

    # Print counts per property (sorted)
    print("Entries per property:")
    print("-" * 50)
    for prop in sorted(property_counts.keys()):
        print(f"  {prop}: {property_counts[prop]}")

    print()
    print(f"Total properties: {len(property_counts)}")
    print(f"Total units: {len(all_units)}")

    if all_errors:
        print()
        print(f"Parse errors ({len(all_errors)}):")
        for err in all_errors:
            print(err)

    # System distribution
    system_counts = {}
    for u in all_units:
        s = u["system"]
        system_counts[s] = system_counts.get(s, 0) + 1
    print()
    print("System distribution:")
    print("-" * 50)
    for sys_name in sorted(system_counts.keys()):
        print(f"  {sys_name}: {system_counts[sys_name]}")


if __name__ == "__main__":
    main()
