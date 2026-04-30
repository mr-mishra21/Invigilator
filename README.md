# Invigilator

> An AI study supervisor for Indian students — automated, multilingual, privacy-first.

## What it is

Invigilator is an Android-based AI supervisor that helps students stay focused and learn better, without requiring a parent to physically sit with them. It watches a study session, gently nudges the student back on track when they drift, blocks distracting apps when nudges fail, and notifies parents only as a last resort. It also acts as an AI tutor that can explain concepts, coach pronunciation, and generate practice questions in any major Indian language.

It is built for two customer segments:

- **Coaching institutes** training candidates for competitive exams (APSC, UPSC, SSC, banking) — sold as a per-student monthly license.
- **Parents directly** — sold as a family subscription for school and exam-prep students.

## Two modes of operation

**Smartphone mode** — the app runs on the student's own phone. It monitors which apps are foregrounded, blocks distracting ones (WhatsApp, YouTube, Instagram, games) during study windows, runs the AI tutor, and delivers voice nudges when the student leaves study mode.

**Camera mode** — the app runs on a dedicated tablet or old phone mounted facing the student's desk. It uses on-device computer vision (MediaPipe + custom TFLite models) to detect posture, attention, and behavior. No video ever leaves the device. Only derived signals (e.g. "looked away for 90 seconds", "phone in hand", "left desk") are sent to the parent dashboard.

The two modes can be combined — phone for the AI tutor and app blocking, tablet for visual monitoring.

## Core features

### For the student
- Voice-prompted study sessions with timed breaks
- AI tutor for concept explanation in their preferred Indian language
- Pronunciation coaching (especially valuable for English and exam interview prep)
- Auto-generated practice questions based on syllabus

### For the supervisor (the "Invigilator" itself)
- Continuous nudge: soft voice → stern voice → forced app block → parent alert
- App blocking via Android Accessibility Service and Usage Stats API
- Camera-based posture, attention, and behavior detection (mode 2)

### For the parent
- Real-time alerts only when student persistently disengages
- Daily and weekly study reports (PDF)
- WhatsApp-style activity feed
- One-tap "Force focus mode" override

### For the institute
- Multi-student dashboard
- Cohort-level engagement reports
- Custom syllabus upload (e.g., APSC mains topics)

## Languages

All 22 scheduled Indian languages, with first-class support for Assamese, Hindi, Bengali, English. ASR and TTS via AI4Bharat (open-source, India-trained models). Larger explanations via Claude API.

## Privacy posture

This is a non-negotiable design constraint, not a feature.

- Camera video is **processed only on-device**. Frames are never uploaded, never stored, never transmitted.
- Only derived events (timestamped strings like `"posture_alert"`, `"phone_detected"`) leave the device.
- Compliance with India's Digital Personal Data Protection Act (DPDP) 2023, including verifiable parental consent for minors and right-to-delete flows.
- No facial recognition. No identity verification from camera. No biometrics stored.

## Tech stack

| Layer | Choice |
|---|---|
| Mobile | Kotlin + Jetpack Compose |
| Architecture | MVVM + Clean Architecture, multi-module Gradle |
| Backend | Firebase (Auth, Firestore, Cloud Functions, FCM, Storage) |
| On-device ML | MediaPipe (Pose, Face Mesh) + TensorFlow Lite (custom classifier) |
| AI tutor | Claude API (Anthropic) |
| Indian language TTS/ASR | AI4Bharat IndicTTS / IndicASR |
| Parent web dashboard | React + Vite + Firebase Hosting |
| App blocking | Android Accessibility Service + Usage Stats API |

## MVP scope (first 4–6 weeks)

The first deliverable is **Smartphone Mode MVP** — proves the core value loop end to end on Android.

In scope:
1. Student onboarding and profile (linked to a parent account)
2. Parent onboarding with verifiable consent flow (DPDP-compliant)
3. Study session timer (start, pause, end)
4. Distracting-app detection during a session
5. Three-stage escalation: voice nudge → forced app close → parent push notification
6. Basic parent dashboard (web): live session status + activity log
7. Hindi and Assamese voice nudges, English UI

Explicitly out of scope for MVP:
- Computer vision / camera mode
- AI tutor
- Pronunciation coaching
- Cohort/institute features
- iOS

## Repository structure

```
invigilator/
├── app/                   # Android app entry, navigation, Compose screens
├── core/                  # Shared domain: auth, models, repositories, DI
├── feature-blocker/       # App blocking service + foreground monitor
├── feature-voice/         # TTS and nudge engine
├── feature-tutor/         # AI tutor (post-MVP)
├── feature-vision/        # Camera ML pipeline (post-MVP)
├── backend/               # Firebase Cloud Functions
├── parent-dashboard/      # React web app for parents
├── docs/                  # Architecture, decisions, compliance
└── scripts/               # Build, test, release helpers
```

## Status

Pre-alpha. Active development started April 2026. Owned and operated by an APSC coaching institute in Guwahati, Assam.

## License

Proprietary. All rights reserved. Not open source.
