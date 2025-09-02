"""
Unit tests for config loading and merging functionality
"""

import pytest
import tempfile
import os
from pathlib import Path
from unittest.mock import patch

from devenv.utils.config import (
    generate_default_config,
    load_project_config,
    load_user_config,
    merge_configs,
    validate_config,
    load_and_merge_config
)


class TestGenerateDefaultConfig:
    """Test default config generation"""
    
    def test_basic_default_config(self):
        """Test basic default config without ports"""
        config = generate_default_config()
        
        assert config["name"] == "devenv"  # Should be current directory name
        assert config["image"] == "mcr.microsoft.com/devcontainers/base:ubuntu-24.04"
        assert "ports" not in config
    
    def test_default_config_with_ports(self):
        """Test default config with ports specified"""
        ports = ["3000:3000", "5432:5432"]
        config = generate_default_config(ports=ports)
        
        assert config["name"] == "devenv"
        assert config["image"] == "mcr.microsoft.com/devcontainers/base:ubuntu-24.04"
        assert config["ports"] == ports
    
    def test_project_name_from_path(self):
        """Test project name is extracted from path"""
        config = generate_default_config("/path/to/my-awesome-project")
        assert config["name"] == "my-awesome-project"


class TestLoadProjectConfig:
    """Test project config loading"""
    
    def test_load_nonexistent_config(self):
        """Test loading config that doesn't exist returns None"""
        config = load_project_config("nonexistent/path/config.yml")
        assert config is None
    
    def test_load_valid_config(self):
        """Test loading valid YAML config"""
        with tempfile.NamedTemporaryFile(mode='w', suffix='.yml', delete=False) as f:
            f.write("""
name: test-project
image: custom-image:latest
ports:
  - "8080:8080"
environment:
  NODE_ENV: development
""")
            config_path = f.name
        
        try:
            config = load_project_config(config_path)
            assert config is not None
            assert config["name"] == "test-project"
            assert config["image"] == "custom-image:latest"
            assert config["ports"] == ["8080:8080"]
            assert config["environment"]["NODE_ENV"] == "development"
        finally:
            os.unlink(config_path)


class TestLoadUserConfig:
    """Test user config loading"""
    
    def test_load_user_config_no_directory(self):
        """Test loading user config when directory doesn't exist"""
        with tempfile.TemporaryDirectory() as temp_dir:
            config = load_user_config(temp_dir)
            assert config is None
    
    def test_load_user_config_empty_directory(self):
        """Test loading user config from empty directory"""
        with tempfile.TemporaryDirectory() as temp_dir:
            config_dir = Path(temp_dir) / ".config" / "devenv"
            config_dir.mkdir(parents=True)
            
            config = load_user_config(temp_dir)
            assert config is None
    
    def test_load_user_config_with_vscode_plugins(self):
        """Test loading user config with VS Code plugins"""
        with tempfile.TemporaryDirectory() as temp_dir:
            config_dir = Path(temp_dir) / ".config" / "devenv"
            config_dir.mkdir(parents=True)
            
            # Create VS Code plugins file
            plugins_file = config_dir / "plugins.vscode.txt"
            plugins_file.write_text("""
# VS Code extensions
ms-python.python
esbenp.prettier-vscode

# Another extension
dbaeumer.vscode-eslint
""")
            
            config = load_user_config(temp_dir)
            assert config is not None
            assert "plugins" in config
            assert "vscode" in config["plugins"]
            expected_plugins = ["ms-python.python", "esbenp.prettier-vscode", "dbaeumer.vscode-eslint"]
            assert config["plugins"]["vscode"] == expected_plugins
    
    def test_load_user_config_with_hooks_and_dotfiles(self):
        """Test loading user config with hooks and dotfiles directories"""
        with tempfile.TemporaryDirectory() as temp_dir:
            config_dir = Path(temp_dir) / ".config" / "devenv"
            config_dir.mkdir(parents=True)
            
            # Create hooks directory
            hooks_dir = config_dir / "hooks"
            hooks_dir.mkdir()
            (hooks_dir / "post_create").touch()
            
            # Create dotfiles directory
            dotfiles_dir = config_dir / "dotfiles"
            dotfiles_dir.mkdir()
            (dotfiles_dir / ".bashrc").touch()
            
            config = load_user_config(temp_dir)
            assert config is not None
            assert "hooks_dir" in config
            assert "dotfiles_dir" in config
            assert config["hooks_dir"] == str(hooks_dir)
            assert config["dotfiles_dir"] == str(dotfiles_dir)


class TestMergeConfigs:
    """Test config merging functionality"""
    
    def test_merge_no_configs(self):
        """Test merging when both configs are None"""
        config = merge_configs(None, None)
        # Should return default config
        assert config["name"] == "devenv"
        assert config["image"] == "mcr.microsoft.com/devcontainers/base:ubuntu-24.04"
    
    def test_merge_project_config_only(self):
        """Test merging with only project config"""
        project_config = {
            "name": "my-app",
            "image": "node:20",
            "ports": ["3000:3000"]
        }
        
        config = merge_configs(project_config, None)
        assert config["name"] == "my-app"
        assert config["image"] == "node:20"
        assert config["ports"] == ["3000:3000"]
    
    def test_merge_user_config_only(self):
        """Test merging with only user config (should not affect app settings)"""
        user_config = {
            "plugins": {
                "vscode": ["ms-python.python"]
            },
            "hooks_dir": "/path/to/hooks"
        }
        
        config = merge_configs(None, user_config)
        # Should still have defaults for app settings
        assert config["name"] == "devenv"
        assert config["image"] == "mcr.microsoft.com/devcontainers/base:ubuntu-24.04"
        # But should include user preferences
        assert config["plugins"]["vscode"] == ["ms-python.python"]
        assert config["hooks_dir"] == "/path/to/hooks"
    
    def test_merge_plugin_combination(self):
        """Test merging plugins from project and user configs"""
        project_config = {
            "name": "my-app",
            "plugins": {
                "vscode": ["ms-python.python", "esbenp.prettier-vscode"]
            }
        }
        
        user_config = {
            "plugins": {
                "vscode": ["dbaeumer.vscode-eslint", "ms-python.python"]  # Duplicate
            }
        }
        
        config = merge_configs(project_config, user_config)
        vscode_plugins = config["plugins"]["vscode"]
        
        # Should combine and deduplicate
        assert len(vscode_plugins) == 3
        assert "ms-python.python" in vscode_plugins
        assert "esbenp.prettier-vscode" in vscode_plugins
        assert "dbaeumer.vscode-eslint" in vscode_plugins


class TestValidateConfig:
    """Test config validation"""
    
    def test_validate_valid_config(self):
        """Test validating a valid config"""
        config = {
            "name": "my-app",
            "image": "node:20"
        }
        
        result = validate_config(config)
        assert result == config
    
    def test_validate_missing_name(self):
        """Test validation fails when name is missing"""
        config = {
            "image": "node:20"
        }
        
        with pytest.raises(ValueError, match="Required field 'name' is missing"):
            validate_config(config)
    
    def test_validate_empty_image(self):
        """Test validation fails when image is empty"""
        config = {
            "name": "my-app",
            "image": ""
        }
        
        with pytest.raises(ValueError, match="Required field 'image' is missing"):
            validate_config(config)
    
    def test_validate_invalid_name_characters(self):
        """Test validation fails for invalid name characters"""
        config = {
            "name": "my app with spaces!",
            "image": "node:20"
        }
        
        with pytest.raises(ValueError, match="contains invalid characters"):
            validate_config(config)
    
    def test_validate_valid_name_characters(self):
        """Test validation passes for valid name characters"""
        config = {
            "name": "my-app_123",
            "image": "node:20"
        }
        
        result = validate_config(config)
        assert result == config


class TestLoadAndMergeConfig:
    """Test the full load and merge functionality"""
    
    def test_load_and_merge_no_configs(self):
        """Test loading when no config files exist"""
        with tempfile.TemporaryDirectory() as temp_dir:
            config = load_and_merge_config(temp_dir, temp_dir)
            
            # Should return default config
            assert config["name"] == os.path.basename(temp_dir)
            assert config["image"] == "mcr.microsoft.com/devcontainers/base:ubuntu-24.04"
    
    def test_load_and_merge_with_project_config(self):
        """Test loading with project config file"""
        with tempfile.TemporaryDirectory() as temp_dir:
            # Create project config
            devenv_dir = Path(temp_dir) / ".devenv"
            devenv_dir.mkdir()
            config_file = devenv_dir / "config.yml"
            config_file.write_text("""
name: test-project
image: custom:latest
ports:
  - "8080:8080"
""")
            
            config = load_and_merge_config(temp_dir, temp_dir)
            assert config["name"] == "test-project"
            assert config["image"] == "custom:latest"
            assert config["ports"] == ["8080:8080"]