# :core

Shared domain layer for Invigilator.

**Responsibilities:** Firebase client wiring, shared models (`User`, `Session`, `ConsentRecord`), repository interfaces and implementations (`AuthRepository`, `SessionRepository`, `ConsentRepository`), Hilt DI modules, DPDP consent state.

**Dependency rule:** This module has no dependency on any `:feature-*` module.
