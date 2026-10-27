# MOBA Analytics Backend

**A Spring Boot API for exploring League of Legends player and match data, with AI-assisted summaries of recent performance.**

This repository contains the backend API. The companion [MOBA Analytics frontend](https://github.com/andrew-nguy/moba-analytics-frontend) provides the user interface.

## Features
- Fetches Riot ID account details, summoner info, ranked history, and full match telemetry.
- Aggregates raw match histories into calculated player metrics, champion win-rates, and role breakdowns.
- Leverages Google Gemini (via Spring AI) to compile natural-language performance insights with built-in fallback.
- Maps downstream Riot API rate limits and structural changes into consistent, reliable HTTP error wrappers.

## CI/CD & Security

GitHub Actions runs the existing test suite on pull requests and pushes to `master`. The test job also generates JaCoCo XML and HTML coverage reports; the HTML report is uploaded as a workflow artifact.

Security and dependency checks include:

- **CodeQL** scans the Java code on pull requests, pushes to `master`, and weekly.
- **Dependabot** checks Gradle dependencies and GitHub Actions for weekly updates. A workflow submits the Gradle dependency graph to GitHub for vulnerability alerts.
- **Trivy** builds and scans the Docker image on pull requests and pushes to `master`, checking the image's operating-system packages and application libraries.

Vulnerability findings are reported to GitHub code scanning and do not fail CI by themselves. Test failures can still fail the test job.

## Deployment Status

The backend is not currently deployed or running as a hosted service. The ECS deployment job is disabled because keeping a live backend online would incur ongoing cloud costs.
