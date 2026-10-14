# Distributed Online Gaming Platform

A distributed system for online games of chance: a Java backend (master/worker nodes over TCP sockets with MapReduce-style processing) and an Android client app.

## Tech stack
- Java (sockets, multithreading)
- Android (Java)

## Features
- Master / worker architecture with in-memory game buffers
- Game search, play, balance top-up and statistics
- Manager console for adding games from JSON
- Android app communicating with the backend over TCP

## Getting started
Backend:
```bash
cd backend-java
./compile.sh && ./run_all.sh
```
Android: open `android-app/` in Android Studio and set the server address in `NetworkClient.java`.

---
Part of the **SIGMA LAB** project portfolio.
