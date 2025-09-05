"""
Unit tests for devcontainer.json generation functionality
"""

import pytest
import json
import tempfile
import os
from pathlib import Path
from unittest.mock import patch
from datetime import datetime

from devenv.utils.devcontainer import (
    extract_ports_from_config,
    generate_docker_labels,
    generate_devcontainer_json,
    temp_devcontainer_json,
    create_devcontainer_from_config,
    get_container_name
)


class TestExtractPortsFromConfig:
    """Test port extraction from config"""
    
    def test_extract_ports_empty(self):
        """Test with no ports"""
        assert extract_ports_from_config([]) == []
        assert extract_ports_from_config(None) == []
    
    def test_extract_ports_with_mappings(self):
        """Test extracting ports from port mappings"""
        ports = ["3000:3000", "5432:5432", "8080:80"]
        result = extract_ports_from_config(ports)
        assert result == [3000, 5432, 8080]
    
    def test_extract_ports_single_numbers(self):
        """Test extracting single port numbers"""
        ports = ["3000", "5432"]
        result = extract_ports_from_config(ports)
        assert result == [3000, 5432]
    
    def test_extract_ports_mixed(self):
        """Test mixing port mappings and single ports"""
        ports = ["3000:3000", "5432", "8080:80"]
        result = extract_ports_from_config(ports)
        assert result == [3000, 5432, 8080]
    
    def test_extract_ports_invalid(self):
        """Test with invalid port specifications"""
        ports = ["invalid", "3000:3000", "not-a-port:80", "5432"]
        result = extract_ports_from_config(ports)
        assert result == [3000, 5432]


class TestGenerateDockerLabels:
    """Test Docker label generation"""
    
    @patch('devenv.utils.devcontainer.datetime')
    def test_generate_docker_labels_basic(self, mock_datetime):
        """Test basic label generation"""
        mock_datetime.utcnow.return_value.isoformat.return_value = "2025-01-15T10:30:00"
        
        labels = generate_docker_labels("main", "my-repo", "/path/to/repo")
        
        expected_labels = [
            "--label=com.devenv.managed=true",
            "--label=com.devenv.repo=my-repo",
            "--label=com.devenv.repo-path=/path/to/repo",
            "--label=com.devenv.branch=main",
            "--label=com.devenv.editor=vscode",
            "--label=com.devenv.created=2025-01-15T10:30:00Z",
            "--label=com.devenv.tainted=false",
            "--label=com.devenv.modules="
        ]
        
        assert labels == expected_labels
    
    def test_generate_docker_labels_with_modules(self):
        """Test label generation with modules"""
        labels = generate_docker_labels(
            "feature-test", "my-repo", "/path", "jetbrains", ["claude-code", "gpu"]
        )
        
        # Check specific labels we care about
        assert "--label=com.devenv.branch=feature-test" in labels
        assert "--label=com.devenv.editor=jetbrains" in labels
        assert "--label=com.devenv.modules=claude-code,gpu" in labels


class TestGenerateDevcontainerJson:
    """Test devcontainer.json generation"""
    
    def test_generate_minimal_devcontainer(self):
        """Test generating minimal devcontainer.json"""
        config = {
            "name": "test-project",
            "image": "ubuntu:22.04"
        }
        
        result = generate_devcontainer_json(config, "main", "test-repo", "/path")
        
        assert result["name"] == "devenv-test-repo-main-vscode"
        assert result["image"] == "ubuntu:22.04"
        assert "forwardPorts" not in result  # No ports specified
        assert len(result["mounts"]) == 1  # Just mise cache
        assert result["mounts"][0]["source"] == "${localEnv:HOME}/.local/share/mise"
    
    def test_generate_devcontainer_with_ports(self):
        """Test generating devcontainer.json with port forwarding"""
        config = {
            "name": "test-project",
            "image": "node:20",
            "ports": ["3000:3000", "5432:5432"]
        }
        
        result = generate_devcontainer_json(config, "main", "test-repo", "/path")
        
        assert result["forwardPorts"] == [3000, 5432]
    
    def test_generate_devcontainer_with_environment(self):
        """Test generating devcontainer.json with environment variables"""
        config = {
            "name": "test-project",
            "image": "node:20",
            "environment": {
                "NODE_ENV": "development",
                "APP_NAME": "test-app"
            }
        }
        
        result = generate_devcontainer_json(config, "main", "test-repo", "/path")
        
        assert result["remoteEnv"]["NODE_ENV"] == "development"
        assert result["remoteEnv"]["APP_NAME"] == "test-app"
    
    def test_generate_devcontainer_with_features(self):
        """Test generating devcontainer.json with features"""
        config = {
            "name": "test-project",
            "image": "ubuntu:22.04",
            "features": {
                "ghcr.io/devcontainers/features/git:1": {},
                "ghcr.io/devcontainers/features/docker-in-docker:2": {}
            }
        }
        
        result = generate_devcontainer_json(config, "main", "test-repo", "/path")
        
        assert "features" in result
        assert "ghcr.io/devcontainers/features/git:1" in result["features"]
        assert "ghcr.io/devcontainers/features/docker-in-docker:2" in result["features"]
    
    def test_generate_devcontainer_with_vscode_plugins(self):
        """Test generating devcontainer.json with VS Code plugins"""
        config = {
            "name": "test-project",
            "image": "node:20",
            "plugins": {
                "vscode": ["ms-python.python", "esbenp.prettier-vscode"]
            }
        }
        
        result = generate_devcontainer_json(config, "main", "test-repo", "/path", "vscode")
        
        assert "customizations" in result
        assert "vscode" in result["customizations"]
        assert result["customizations"]["vscode"]["extensions"] == ["ms-python.python", "esbenp.prettier-vscode"]
    
    def test_generate_devcontainer_with_jetbrains_plugins(self):
        """Test generating devcontainer.json with JetBrains plugins"""
        config = {
            "name": "test-project",
            "image": "node:20",
            "plugins": {
                "jetbrains": ["com.jetbrains.plugins.node"]
            }
        }
        
        result = generate_devcontainer_json(config, "main", "test-repo", "/path", "jetbrains")
        
        assert "customizations" in result
        assert "jetbrains" in result["customizations"]
        assert result["customizations"]["jetbrains"]["plugins"] == ["com.jetbrains.plugins.node"]
    
    def test_generate_devcontainer_with_dotfiles(self):
        """Test generating devcontainer.json with dotfiles directory"""
        config = {
            "name": "test-project",
            "image": "ubuntu:22.04",
            "dotfiles_dir": "/home/user/.config/devenv/dotfiles"
        }
        
        result = generate_devcontainer_json(config, "main", "test-repo", "/path")
        
        # Should have mise cache + dotfiles mount
        assert len(result["mounts"]) == 2
        dotfiles_mount = result["mounts"][1]
        assert dotfiles_mount["source"] == "/home/user/.config/devenv/dotfiles"
        assert dotfiles_mount["target"] == "/tmp/devenv-dotfiles"
        
        # Should include dotfiles copy in post-create command
        assert "cp -r /tmp/devenv-dotfiles/. ~/" in result["postCreateCommand"]
    
    def test_generate_devcontainer_post_create_command(self):
        """Test post-create command generation"""
        config = {
            "name": "test-project",
            "image": "node:20",
            "post_create_command": "npm install"
        }
        
        result = generate_devcontainer_json(config, "main", "test-repo", "/path")
        
        post_create = result["postCreateCommand"]
        # Should include mise setup and user command
        assert "curl -fsSL https://mise.run | sh" in post_create
        assert "~/.local/bin/mise install" in post_create
        assert "npm install" in post_create
    
    def test_generate_devcontainer_docker_labels(self):
        """Test Docker labels are included in runArgs"""
        config = {
            "name": "test-project",
            "image": "ubuntu:22.04"
        }
        
        result = generate_devcontainer_json(config, "main", "test-repo", "/path/to/repo")
        
        run_args = result["runArgs"]
        assert "--label=com.devenv.managed=true" in run_args
        assert "--label=com.devenv.repo=test-repo" in run_args
        assert "--label=com.devenv.branch=main" in run_args
        assert "--label=com.devenv.repo-path=/path/to/repo" in run_args


class TestTempDevcontainerJson:
    """Test temporary devcontainer.json file management"""
    
    def test_temp_devcontainer_json(self):
        """Test creating and cleaning up temporary devcontainer.json"""
        config = {
            "name": "test-container",
            "image": "ubuntu:22.04"
        }
        
        # Test the context manager
        with temp_devcontainer_json(config) as temp_path:
            # File should exist and be readable
            assert os.path.exists(temp_path)
            
            with open(temp_path, 'r') as f:
                loaded_config = json.load(f)
            
            assert loaded_config["name"] == "test-container"
            assert loaded_config["image"] == "ubuntu:22.04"
        
        # File should be cleaned up after context manager exits
        assert not os.path.exists(temp_path)


class TestCreateDevcontainerFromConfig:
    """Test the main devcontainer creation function"""
    
    def test_create_devcontainer_from_config(self):
        """Test creating devcontainer from project config"""
        with tempfile.TemporaryDirectory() as temp_dir:
            # Create project config
            devenv_dir = Path(temp_dir) / ".devenv"
            devenv_dir.mkdir()
            config_file = devenv_dir / "config.yml"
            config_file.write_text("""
name: test-project
image: node:20
ports:
  - "3000:3000"
plugins:
  vscode:
    - ms-python.python
""")
            
            # Test creating devcontainer
            with create_devcontainer_from_config(temp_dir, "feature-test", "vscode") as devcontainer_path:
                assert os.path.exists(devcontainer_path)
                
                with open(devcontainer_path, 'r') as f:
                    config = json.load(f)
                
                project_name = os.path.basename(temp_dir)
                expected_name = f"devenv-{project_name}-feature-test-vscode"
                
                assert config["name"] == expected_name
                assert config["image"] == "node:20"
                assert config["forwardPorts"] == [3000]
                assert config["customizations"]["vscode"]["extensions"] == ["ms-python.python"]


class TestGetContainerName:
    """Test container name generation"""
    
    def test_get_container_name_basic(self):
        """Test basic container name generation"""
        name = get_container_name("my-repo", "main")
        assert name == "devenv-my-repo-main-vscode"
    
    def test_get_container_name_with_editor(self):
        """Test container name with different editor"""
        name = get_container_name("my-repo", "feature-auth", "jetbrains")
        assert name == "devenv-my-repo-feature-auth-jetbrains"