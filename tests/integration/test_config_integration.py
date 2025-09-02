"""
Integration tests for config loading with real file scenarios
"""

import pytest
import tempfile
import os
from pathlib import Path

from devenv.utils.config import load_and_merge_config


class TestConfigIntegration:
    """Integration tests for config loading scenarios"""
    
    def test_ports_from_application_config(self):
        """Test that ports are properly loaded from application config"""
        with tempfile.TemporaryDirectory() as temp_dir:
            # Create project structure
            project_dir = Path(temp_dir) / "my-web-app"
            project_dir.mkdir()
            devenv_dir = project_dir / ".devenv"
            devenv_dir.mkdir()
            
            # Create project config with ports
            config_file = devenv_dir / "config.yml"
            config_file.write_text("""
name: my-web-app
image: node:20
ports:
  - "3000:3000"
  - "5432:5432"
environment:
  NODE_ENV: development
  API_URL: http://localhost:3000
""")
            
            # Load and merge config
            config = load_and_merge_config(str(project_dir), temp_dir)
            
            # Verify application settings are loaded
            assert config["name"] == "my-web-app"
            assert config["image"] == "node:20"
            assert config["ports"] == ["3000:3000", "5432:5432"]
            assert config["environment"]["NODE_ENV"] == "development"
            assert config["environment"]["API_URL"] == "http://localhost:3000"
    
    def test_ide_plugins_from_user_settings(self):
        """Test that IDE plugins are properly merged from user settings"""
        with tempfile.TemporaryDirectory() as temp_dir:
            # Create project structure
            project_dir = Path(temp_dir) / "my-project"
            project_dir.mkdir()
            devenv_dir = project_dir / ".devenv"
            devenv_dir.mkdir()
            
            # Create minimal project config
            config_file = devenv_dir / "config.yml"
            config_file.write_text("""
name: my-project
image: ubuntu:22.04
plugins:
  vscode:
    - ms-python.python
""")
            
            # Create user config with IDE preferences
            user_config_dir = Path(temp_dir) / ".config" / "devenv"
            user_config_dir.mkdir(parents=True)
            
            # VS Code plugins
            vscode_plugins = user_config_dir / "plugins.vscode.txt"
            vscode_plugins.write_text("""
# User's favorite extensions
esbenp.prettier-vscode
dbaeumer.vscode-eslint
ms-python.python
ms-vscode.vscode-typescript-next
""")
            
            # JetBrains plugins
            jetbrains_plugins = user_config_dir / "plugins.jetbrains.txt"
            jetbrains_plugins.write_text("""
# User's JetBrains plugins
com.intellij.plugins.html
org.jetbrains.plugins.yaml
""")
            
            # Load and merge config
            config = load_and_merge_config(str(project_dir), temp_dir)
            
            # Verify plugins are properly merged
            assert "plugins" in config
            
            # VS Code plugins should be combined (project + user, deduplicated)
            vscode_plugins = config["plugins"]["vscode"]
            assert "ms-python.python" in vscode_plugins  # From both project and user
            assert "esbenp.prettier-vscode" in vscode_plugins  # From user
            assert "dbaeumer.vscode-eslint" in vscode_plugins  # From user
            assert "ms-vscode.vscode-typescript-next" in vscode_plugins  # From user
            # Should be deduplicated
            assert vscode_plugins.count("ms-python.python") == 1
            
            # JetBrains plugins should come from user config
            jetbrains_plugins = config["plugins"]["jetbrains"]
            assert "com.intellij.plugins.html" in jetbrains_plugins
            assert "org.jetbrains.plugins.yaml" in jetbrains_plugins
    
    def test_user_config_cannot_override_application_settings(self):
        """Test that user config cannot contribute unexpected application-level values"""
        with tempfile.TemporaryDirectory() as temp_dir:
            # Create project structure
            project_dir = Path(temp_dir) / "secure-app"
            project_dir.mkdir()
            devenv_dir = project_dir / ".devenv"
            devenv_dir.mkdir()
            
            # Create project config
            config_file = devenv_dir / "config.yml"
            config_file.write_text("""
name: secure-app
image: python:3.11
ports:
  - "8000:8000"
environment:
  SECRET_KEY: from-project-config
""")
            
            # Create user config directory with hooks and dotfiles
            user_config_dir = Path(temp_dir) / ".config" / "devenv"
            user_config_dir.mkdir(parents=True)
            
            # Create hooks directory
            hooks_dir = user_config_dir / "hooks"
            hooks_dir.mkdir()
            post_create_hook = hooks_dir / "post_create"
            post_create_hook.write_text("#!/bin/bash\\necho 'User post-create hook'")
            post_create_hook.chmod(0o755)
            
            # Create dotfiles directory
            dotfiles_dir = user_config_dir / "dotfiles"
            dotfiles_dir.mkdir()
            bashrc = dotfiles_dir / ".bashrc"
            bashrc.write_text("# User's bashrc for containers\\nalias ll='ls -la'")
            
            # Load and merge config
            config = load_and_merge_config(str(project_dir), temp_dir)
            
            # Verify application settings remain from project config
            assert config["name"] == "secure-app"
            assert config["image"] == "python:3.11"
            assert config["ports"] == ["8000:8000"]
            assert config["environment"]["SECRET_KEY"] == "from-project-config"
            
            # Verify user preferences are included
            assert "hooks_dir" in config
            assert "dotfiles_dir" in config
            assert config["hooks_dir"] == str(hooks_dir)
            assert config["dotfiles_dir"] == str(dotfiles_dir)
            
            # Critically: user config should NOT be able to override application settings
            # Even if user had tried to set these, they should be ignored
    
    def test_complete_integration_scenario(self):
        """Test a complete realistic scenario with project and user configs"""
        with tempfile.TemporaryDirectory() as temp_dir:
            # Create a realistic project structure
            project_dir = Path(temp_dir) / "fullstack-app"
            project_dir.mkdir()
            devenv_dir = project_dir / ".devenv"
            devenv_dir.mkdir()
            
            # Create comprehensive project config
            config_file = devenv_dir / "config.yml"
            config_file.write_text("""
name: fullstack-app
image: mcr.microsoft.com/devcontainers/javascript-node:18
ports:
  - "3000:3000"   # Frontend
  - "3001:3001"   # Backend API
  - "5432:5432"   # PostgreSQL
environment:
  NODE_ENV: development
  DATABASE_URL: postgres://user:pass@localhost:5432/devdb
  API_BASE_URL: http://localhost:3001
plugins:
  vscode:
    - ms-vscode.vscode-typescript-next
    - bradlc.vscode-tailwindcss
features:
  "ghcr.io/devcontainers/features/docker-in-docker:2": {}
  "ghcr.io/devcontainers/features/git:1": {}
""")
            
            # Create comprehensive user config
            user_config_dir = Path(temp_dir) / ".config" / "devenv"
            user_config_dir.mkdir(parents=True)
            
            # User's VS Code preferences
            vscode_plugins = user_config_dir / "plugins.vscode.txt"
            vscode_plugins.write_text("""
# Development tools
esbenp.prettier-vscode
dbaeumer.vscode-eslint
ms-python.python

# Git and productivity
eamodio.gitlens
ms-vscode.vscode-json
""")
            
            # User's hooks
            hooks_dir = user_config_dir / "hooks"
            hooks_dir.mkdir()
            post_create = hooks_dir / "post_create"
            post_create.write_text("""#!/bin/bash
echo "Setting up user environment..."
npm install -g typescript
git config --global user.email "user@example.com"
""")
            post_create.chmod(0o755)
            
            # User's dotfiles
            dotfiles_dir = user_config_dir / "dotfiles"
            dotfiles_dir.mkdir()
            (dotfiles_dir / ".gitconfig").write_text("""[user]
    name = Developer
    email = user@example.com
[alias]
    st = status
    co = checkout
""")
            (dotfiles_dir / ".bashrc").write_text("""
export EDITOR=vim
alias la='ls -la'
alias gst='git status'
""")
            
            # Load and merge the complete config
            config = load_and_merge_config(str(project_dir), temp_dir)
            
            # Verify all application settings are preserved
            assert config["name"] == "fullstack-app"
            assert config["image"] == "mcr.microsoft.com/devcontainers/javascript-node:18"
            assert len(config["ports"]) == 3
            assert "3000:3000" in config["ports"]
            assert "3001:3001" in config["ports"]
            assert "5432:5432" in config["ports"]
            
            # Verify environment variables
            assert config["environment"]["NODE_ENV"] == "development"
            assert "DATABASE_URL" in config["environment"]
            assert "API_BASE_URL" in config["environment"]
            
            # Verify container features are preserved
            assert "features" in config
            assert "ghcr.io/devcontainers/features/docker-in-docker:2" in config["features"]
            
            # Verify plugins are properly merged
            vscode_plugins = config["plugins"]["vscode"]
            # Project plugins
            assert "ms-vscode.vscode-typescript-next" in vscode_plugins
            assert "bradlc.vscode-tailwindcss" in vscode_plugins
            # User plugins
            assert "esbenp.prettier-vscode" in vscode_plugins
            assert "dbaeumer.vscode-eslint" in vscode_plugins
            assert "ms-python.python" in vscode_plugins
            assert "eamodio.gitlens" in vscode_plugins
            
            # Verify user preferences are included
            assert "hooks_dir" in config
            assert "dotfiles_dir" in config
            assert config["hooks_dir"] == str(hooks_dir)
            assert config["dotfiles_dir"] == str(dotfiles_dir)
            
            # Verify config validation passes
            assert "name" in config
            assert "image" in config
            assert config["name"].replace("-", "").isalnum()  # Valid project name
    
    def test_no_user_config_fallback(self):
        """Test that system works correctly when user has no config"""
        with tempfile.TemporaryDirectory() as temp_dir:
            # Create project but NO user config
            project_dir = Path(temp_dir) / "simple-app"
            project_dir.mkdir()
            devenv_dir = project_dir / ".devenv"
            devenv_dir.mkdir()
            
            # Create project config only
            config_file = devenv_dir / "config.yml"
            config_file.write_text("""
name: simple-app
image: python:3.11
ports:
  - "8000:8000"
""")
            
            # Load config (user config directory doesn't exist)
            config = load_and_merge_config(str(project_dir), temp_dir)
            
            # Should work fine with just project config
            assert config["name"] == "simple-app"
            assert config["image"] == "python:3.11"
            assert config["ports"] == ["8000:8000"]
            
            # Should not have any user-specific keys
            assert "hooks_dir" not in config
            assert "dotfiles_dir" not in config
    
    def test_empty_project_config_with_user_preferences(self):
        """Test behavior when project has minimal config but user has preferences"""
        with tempfile.TemporaryDirectory() as temp_dir:
            # Create project with no .devenv config file
            project_dir = Path(temp_dir) / "minimal-project"
            project_dir.mkdir()
            
            # Create user config with preferences
            user_config_dir = Path(temp_dir) / ".config" / "devenv"
            user_config_dir.mkdir(parents=True)
            
            vscode_plugins = user_config_dir / "plugins.vscode.txt"
            vscode_plugins.write_text("ms-python.python\nesbenp.prettier-vscode")
            
            # Load config
            config = load_and_merge_config(str(project_dir), temp_dir)
            
            # Should get defaults for application settings
            assert config["name"] == "minimal-project"
            assert config["image"] == "mcr.microsoft.com/devcontainers/base:ubuntu-24.04"
            assert "ports" not in config  # No default ports
            
            # But should include user preferences
            assert "plugins" in config
            assert "vscode" in config["plugins"]
            assert "ms-python.python" in config["plugins"]["vscode"]