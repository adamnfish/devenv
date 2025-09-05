"""
Unit tests for module system functionality
"""

import pytest
from devenv.utils.modules import (
    get_available_modules,
    get_module,
    validate_modules,
    apply_modules_to_devcontainer,
    get_module_summary,
    BUILTIN_MODULES
)


class TestGetAvailableModules:
    """Test getting available modules"""
    
    def test_get_available_modules(self):
        """Test getting all available modules"""
        modules = get_available_modules()
        
        # Should have the expected built-in modules
        assert "claude-code" in modules
        assert "docker-in-docker" in modules
        
        # Should be a copy (not the original dict)
        assert modules is not BUILTIN_MODULES
        
        # Each module should have a description
        for module_name, config in modules.items():
            assert "description" in config
            assert isinstance(config["description"], str)


class TestGetModule:
    """Test getting specific modules"""
    
    def test_get_module_valid(self):
        """Test getting a valid module"""
        module = get_module("claude-code")
        
        assert "description" in module
        assert "mounts" in module
        assert "environment" in module
        assert module["environment"]["CLAUDE_CODE_ENABLED"] == "true"
    
    def test_get_module_invalid(self):
        """Test getting an invalid module raises KeyError"""
        with pytest.raises(KeyError) as exc_info:
            get_module("nonexistent-module")
        
        assert "nonexistent-module" in str(exc_info.value)
        assert "Available modules:" in str(exc_info.value)
    
    def test_get_module_returns_copy(self):
        """Test that get_module returns a copy"""
        module1 = get_module("claude-code")
        module2 = get_module("claude-code")
        
        assert module1 is not module2
        assert module1 == module2


class TestValidateModules:
    """Test module validation"""
    
    def test_validate_modules_valid(self):
        """Test validating valid modules"""
        modules = ["claude-code", "docker-in-docker"]
        result = validate_modules(modules)
        assert result == modules
    
    def test_validate_modules_empty(self):
        """Test validating empty module list"""
        result = validate_modules([])
        assert result == []
    
    def test_validate_modules_invalid(self):
        """Test validating invalid modules raises ValueError"""
        with pytest.raises(ValueError) as exc_info:
            validate_modules(["claude-code", "invalid-module", "another-invalid"])
        
        error_msg = str(exc_info.value)
        assert "Unknown modules:" in error_msg
        assert "invalid-module" in error_msg
        assert "another-invalid" in error_msg
        assert "Available modules:" in error_msg
    
    def test_validate_modules_mixed(self):
        """Test validating mix of valid and invalid modules"""
        with pytest.raises(ValueError) as exc_info:
            validate_modules(["claude-code", "invalid-module"])
        
        error_msg = str(exc_info.value)
        assert "Unknown modules: invalid-module" in error_msg
        assert "Available modules:" in error_msg


class TestApplyModulesToDevcontainer:
    """Test applying modules to devcontainer configuration"""
    
    def test_apply_modules_empty(self):
        """Test applying no modules doesn't change config"""
        config = {"name": "test", "image": "ubuntu:22.04"}
        result = apply_modules_to_devcontainer(config, [])
        assert result == config
    
    def test_apply_modules_claude_code(self):
        """Test applying claude-code module"""
        config = {"name": "test", "image": "ubuntu:22.04"}
        result = apply_modules_to_devcontainer(config, ["claude-code"])
        
        # Should have claude mount
        assert "mounts" in result
        claude_mount = next((m for m in result["mounts"] if ".claude" in m["source"]), None)
        assert claude_mount is not None
        
        # Should have environment variable
        assert "remoteEnv" in result
        assert result["remoteEnv"]["CLAUDE_CODE_ENABLED"] == "true"
        
        # Should have post-create command
        assert "postCreateCommand" in result
        assert "@anthropic/claude-code" in result["postCreateCommand"]
    
    
    def test_apply_modules_docker_in_docker(self):
        """Test applying docker-in-docker module"""
        config = {"name": "test", "image": "ubuntu:22.04"}
        result = apply_modules_to_devcontainer(config, ["docker-in-docker"])
        
        # Should have docker-in-docker feature
        assert "features" in result
        assert any("docker-in-docker" in feature for feature in result["features"])
        
        # Should have docker socket mount
        assert "mounts" in result
        docker_mount = next((m for m in result["mounts"] if "docker.sock" in m["source"]), None)
        assert docker_mount is not None
    
    def test_apply_modules_multiple(self):
        """Test applying multiple modules"""
        config = {"name": "test", "image": "ubuntu:22.04"}
        result = apply_modules_to_devcontainer(config, ["claude-code", "docker-in-docker"])
        
        # Should have both modules' configurations
        assert "mounts" in result
        assert "features" in result
        assert "remoteEnv" in result
        
        # Claude Code
        assert result["remoteEnv"]["CLAUDE_CODE_ENABLED"] == "true"
        assert any(".claude" in m["source"] for m in result["mounts"])
        
        # Docker-in-Docker
        assert any("docker-in-docker" in feature for feature in result["features"])
        assert any("docker.sock" in m["source"] for m in result["mounts"])
    
    def test_apply_modules_merge_with_existing(self):
        """Test applying modules merges with existing configuration"""
        config = {
            "name": "test",
            "image": "ubuntu:22.04",
            "mounts": [{"source": "/existing", "target": "/existing", "type": "bind"}],
            "remoteEnv": {"EXISTING_VAR": "value"},
            "postCreateCommand": "echo 'existing command'"
        }
        
        result = apply_modules_to_devcontainer(config, ["claude-code"])
        
        # Should preserve existing mounts and add new ones
        assert len(result["mounts"]) == 2
        existing_mount = next((m for m in result["mounts"] if m["source"] == "/existing"), None)
        assert existing_mount is not None
        
        # Should preserve existing env vars and add new ones
        assert result["remoteEnv"]["EXISTING_VAR"] == "value"
        assert result["remoteEnv"]["CLAUDE_CODE_ENABLED"] == "true"
        
        # Should merge post-create commands
        assert "existing command" in result["postCreateCommand"]
        assert "@anthropic/claude-code" in result["postCreateCommand"]
    
    def test_apply_modules_invalid(self):
        """Test applying invalid modules raises ValueError"""
        config = {"name": "test", "image": "ubuntu:22.04"}
        
        with pytest.raises(ValueError):
            apply_modules_to_devcontainer(config, ["invalid-module"])


class TestGetModuleSummary:
    """Test getting module summaries"""
    
    def test_get_module_summary_valid(self):
        """Test getting summary for valid module"""
        summary = get_module_summary("claude-code")
        assert "Claude Code" in summary
        assert "AI-assisted development" in summary
    
    def test_get_module_summary_invalid(self):
        """Test getting summary for invalid module"""
        summary = get_module_summary("invalid-module")
        assert "Unknown module" in summary
        assert "invalid-module" in summary