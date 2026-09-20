---
name: stack
description: Control implementation boundaries.
triggers: [Java, JavaScript, Maven, graph]
last_updated: 2026-09-20
---

# Implementation boundary

Control is Java 17 with a dependency-free browser client in `src/main/resources/web/app.js`. CI uses `mvn -B clean verify`; root `AGENTS.md` owns full validation details. MEX 0.8.2 can index JavaScript WebUI symbols here but not Java classes, so inspect Java source and tests directly. Source: `pom.xml`, `.github/workflows/maven.yml`, root `AGENTS.md`.
