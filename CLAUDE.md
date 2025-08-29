# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Consult `SPEC.md` to understand what we're building - a CLI tool for managing ephemeral, branch-scoped Dev Container environments.

## Development Environment

This project uses mise for toolchain management. Python 3.11 is configured in `.mise.toml`.

To run Python commands in this project:
- `mise exec -- python <command>` - Run Python with mise-managed version
- `mise exec -- devenv <args>` - Run the devenv CLI
- `mise exec -- python -m pip install -e .` - Install the package in development mode

The project is installed in development mode, so changes to the code are reflected immediately.

## Development Workflow

- Follow the implementation plan in `TODO.md`
- When you complete a feature, notify the user so they can review it
- Wait for user approval before updating `TODO.md` with progress or new tasks
- Update `TODO.md` at the end of every completed feature to track progress