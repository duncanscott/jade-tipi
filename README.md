# Jade-Tipi

**An open playbook for trustworthy, shareable scientific metadata.**

Jade-Tipi (JDTP) is a community effort to make research metadata as portable and actionable as the science it describes. The project pairs a protocol, reference implementation, and documentation set so institutions can describe samples, experiments, and provenance in a way that is transparent to both humans and machines. We emphasize the FAIR principles—Findable, Accessible, Interoperable, Reusable—while keeping the daily realities of lab teams front and center.

## Why Jade-Tipi Exists

Modern labs capture torrents of contextual data, yet that knowledge often hides inside bespoke systems or brittle exports. Jade-Tipi removes that friction by defining shared patterns for identifiers, relationships, and stewardship so that:

- **Intent is preserved**: records document why actions happened, not just final results.
- **Collaboration scales**: multiple groups can enrich the same record without losing provenance or permissions.
- **Reuse becomes routine**: machine-actionable JSON descriptions flow into downstream tools, lakehouses, or analytics stacks.
- **Adaptability is built in**: new data shapes are welcomed without disruptive schema rewrites or vendor lock-in.

These objectives are explored in depth inside `docs/`, especially the living narrative in `docs/Jade-Tipi.md`, the concise restatement in `docs/README.md`, and the current snapshot in `docs/OVERVIEW.md`.

## What You’ll Find Here

This repository gathers everything needed to experiment with the idea and share it with collaborators:

- **Reference services (`jade-tipi/`)** show how a reactive backend, identity, and storage layers can uphold Jade-Tipi’s guarantees.
- **A lightweight UI (`frontend/`)** previews how curators might browse and enrich documents created with the protocol.
- **Early CLI prototypes (`clients/`)** illustrate scripted workflows for data engineers and lab teams.
- **Guides and reviews (`docs/` and `PROJECT_REVIEW.md`)** capture the long-term vision, principles, and roadmap questions.

Think of the code as a conversation starter: it evolves quickly and exists to demonstrate how the broader ideas can come together in practice.

## How to Learn More

If you are new to the project, we recommend this path:

1. Read `docs/Jade-Tipi.md` for the manifesto, terminology, and design motivations.
2. Skim `docs/README.md` and `docs/OVERVIEW.md` to understand the current scope of the reference stack.
3. Explore the services, UI, or clients that align with your interests; each reinforces the same guiding objective of portable, mergeable metadata.

For hands-on setup instructions, architecture diagrams, and API details, follow the pointers in the documents above. They stay authoritative so this README can remain focused on the “why.”

## Community & Licensing

Jade-Tipi is dual-licensed under AGPL-3.0 for open collaboration and a commercial license for closed deployments (see `DUAL-LICENSE.txt`). Contributions, critiques, and field stories are welcome—every new perspective helps refine the protocol and keep it grounded in real research challenges.

Let us know how you are thinking about transparent metadata, and we will continue evolving Jade-Tipi together.
