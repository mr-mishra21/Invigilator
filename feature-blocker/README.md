# :feature-blocker

Foreground app monitor and escalation engine.

**Responsibilities:** `AppMonitorService` (foreground service), `BlockerAccessibilityService`, `EscalationEngine` state machine (voice nudge → force-close → parent FCM alert).

**Dependency rule:** May depend on `:core` only. Must not import any other `:feature-*` module.
