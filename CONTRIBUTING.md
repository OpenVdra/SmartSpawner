# Contributing to SmartSpawner

Thank you for your interest in contributing to SmartSpawner. We welcome contributions of all sizes, from bug fixes and documentation improvements to new APIs and ecosystem enhancements.

Our goal is to keep SmartSpawner fast, maintainable, and extensible. The guidelines below help ensure that contributions align with those goals.

---

## Project Principles

### Performance-Oriented Design

Performance is one of the core goals of SmartSpawner. New features and changes should be designed with execution speed, memory usage, and scalability in mind.

Contributions do not need to be micro-optimized, but they should avoid introducing unnecessary overhead, complexity, or regressions.

### Modular and Extensible Architecture

SmartSpawner aims to keep the core lightweight and focused.

Features that target specialized, niche, or optional use cases are often better implemented as addons rather than built directly into the core project.

Whenever possible, we prefer improving extension points and developer APIs so the ecosystem can grow without increasing core complexity.

### Developer Experience

A good developer experience is important for both core contributors and addon developers.

Contributions that improve documentation, tooling, testing, or developer APIs are encouraged when they remain aligned with the project's performance and architectural goals.

---

## Core vs Addons

As a general guideline, a feature should be considered for an addon if it:

* Serves a specific or niche use case.
* Adds significant complexity to the core.
* Can be implemented using existing extension points or APIs.
* Is not required by most SmartSpawner users.

Features that are broadly useful, foundational, and beneficial to the majority of users may be appropriate for inclusion in the core.

When in doubt, prefer extending developer APIs rather than expanding core functionality.

---

## Code Standards

### Performance

* Avoid unnecessary allocations and excessive work in critical paths.
* Consider the runtime and memory impact of your changes.
* Profile performance-sensitive code when appropriate.
* Prefer simple and maintainable solutions over premature optimization.

### API Design

* Keep public APIs clear, predictable, and efficient.
* Consider long-term maintainability before introducing new APIs.
* Avoid designs that make accidental misuse likely.
* Preserve backward compatibility whenever practical.

### Documentation

* Document all public APIs.
* Update existing documentation when behavior changes.
* Add comments where complex logic would otherwise be difficult to understand.

### Testing

* Verify changes in realistic scenarios whenever possible.
* Ensure existing tests continue to pass.
* Add tests for new behavior when appropriate.
* For performance-sensitive changes, include benchmark results and a brief explanation of how they were measured.

---

## Review Process

All contributions are reviewed based on:

* Correctness
* Maintainability
* Performance impact
* Architectural fit
* Long-term support cost

Acceptance is not determined solely by the size of a contribution. Small improvements that align with the project's goals are highly valued.

---

## Code of Conduct

Please be respectful, professional, and constructive when interacting with other contributors.

Technical discussions and disagreements are expected, but they should focus on ideas, trade-offs, and project goals rather than individuals.

Harassment, abusive behavior, personal attacks, or dismissive conduct will not be tolerated.

Together, we can build a welcoming and productive community around SmartSpawner.
