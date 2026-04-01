# Cloudflar Guide for PintxoMatch

This guide explains how to expose your local image server with Cloudflare Tunnel so physical phones can upload and view images without USB, adb reverse, or local IP changes.

## Why use Cloudflare Tunnel

- Works from any mobile network (Wi-Fi or mobile data).
- Gives you HTTPS URL.
- No need to open router ports.
- Avoids localhost and 10.0.2.2 issues.

## Prerequisites

- Docker Desktop running
- Image server working on port 8080
- cloudflared installed on Windows

Install cloudflared on Windows (PowerShell):

  winget install Cloudflare.cloudflared

## Fast start (recommended)

From project root, run one command:

  .\scripts\start-dev-cloudflare.ps1

What this script does for you:

- Starts Docker image server.
- Starts cloudflared tunnel.
- Detects the generated trycloudflare URL.
- Updates LOCAL_IMAGE_BASE_URL in local.properties.
- Recreates container with PUBLIC_BASE_URL set to same URL.

After it finishes, rebuild and run the Android app.

## Manual flow (fallback)

## Start your local image server

From project root:

  docker compose -f docker-compose.image-server.yml up -d

Health check:

  Invoke-RestMethod -Method Get -Uri "http://localhost:8080/health"

## Quick tunnel (temporary URL)

Run:

  cloudflared tunnel --url http://localhost:8080

cloudflared will print an HTTPS URL similar to:

  https://random-name.trycloudflare.com

Keep this terminal open while testing.

## Configure the app with the tunnel URL

1. Open local.properties
2. Set LOCAL_IMAGE_BASE_URL to your HTTPS tunnel URL.

Example:

  LOCAL_IMAGE_BASE_URL=https://random-name.trycloudflare.com

3. Rebuild the app.

## Configure server-generated image URLs

Set environment variable PUBLIC_BASE_URL to the same HTTPS tunnel URL.

Example:

  $env:PUBLIC_BASE_URL="https://random-name.trycloudflare.com"

Then recreate container:

  docker compose -f docker-compose.image-server.yml up -d

## Important behavior

- Temporary tunnel URL changes every time you restart cloudflared.
- If URL changes, update both places again:
  - local.properties (LOCAL_IMAGE_BASE_URL)
  - PUBLIC_BASE_URL environment variable for docker compose

## Stable tunnel URL (recommended later)

If you want a fixed domain, create a named Cloudflare Tunnel with your Cloudflare account and DNS. That removes the need to update URLs every session.

## Security notes

- Upload and delete endpoints are protected by x-api-key.
- Image files under /uploads are public by URL.
- Do not publish your API key in public repos.

## Troubleshooting

If upload works but image is not shown:

1. Confirm cloudflared terminal is still running.
2. Confirm PUBLIC_BASE_URL and LOCAL_IMAGE_BASE_URL are exactly the same host.
3. Confirm image URL stored in Firestore starts with your current tunnel URL.
4. Rebuild app after changing build.gradle.kts.

If you see unauthorized errors:

1. Verify LOCAL_IMAGE_API_KEY in app/build.gradle.kts
2. Verify IMAGE_SERVER_API_KEY in docker-compose.image-server.yml
3. Values must match exactly.
