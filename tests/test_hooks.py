"""
Tests for the hook system functionality.
"""

import os
import tempfile
import stat
from pathlib import Path
import pytest

from devenv.utils.hooks import (
    find_hook_scripts,
    is_executable,
    generate_hook_command,
    generate_post_create_command_with_hooks,
    generate_post_start_command_with_hooks,
    validate_hook_environment
)


@pytest.fixture
def temp_dirs():
    """Create temporary directories for testing hook discovery."""
    with tempfile.TemporaryDirectory() as temp_dir:
        # Create directory structure
        home_dir = os.path.join(temp_dir, "home")
        project_dir = os.path.join(temp_dir, "project")
        
        # Create hook directories
        os.makedirs(os.path.join(home_dir, ".config", "devenv", "hooks"), exist_ok=True)
        os.makedirs(os.path.join(project_dir, ".devenv", "hooks"), exist_ok=True)
        
        yield {
            "home": home_dir,
            "project": project_dir,
            "temp": temp_dir
        }


def create_hook_script(path: str, content: str, executable: bool = True):
    """Create a hook script file with optional executable permission."""
    with open(path, 'w') as f:
        f.write(content)
    
    if executable:
        # Make file executable
        current_mode = os.stat(path).st_mode
        os.chmod(path, current_mode | stat.S_IEXEC)


class TestHookDiscovery:
    """Test hook script discovery functionality."""
    
    def test_find_no_hooks(self, temp_dirs):
        """Test when no hook scripts exist."""
        hooks = find_hook_scripts("post_create", temp_dirs["project"], temp_dirs["home"])
        assert hooks == []
    
    def test_find_user_hook_only(self, temp_dirs):
        """Test finding only user-level hook."""
        user_hook = os.path.join(temp_dirs["home"], ".config", "devenv", "hooks", "post_create")
        create_hook_script(user_hook, "#!/bin/bash\necho 'user hook'")
        
        hooks = find_hook_scripts("post_create", temp_dirs["project"], temp_dirs["home"])
        assert len(hooks) == 1
        assert hooks[0] == ("user", user_hook)
    
    def test_find_project_hook_only(self, temp_dirs):
        """Test finding only project-level hook."""
        project_hook = os.path.join(temp_dirs["project"], ".devenv", "hooks", "post_create")
        create_hook_script(project_hook, "#!/bin/bash\necho 'project hook'")
        
        hooks = find_hook_scripts("post_create", temp_dirs["project"], temp_dirs["home"])
        assert len(hooks) == 1
        assert hooks[0] == ("project", project_hook)
    
    def test_find_both_hooks(self, temp_dirs):
        """Test finding both user and project hooks."""
        user_hook = os.path.join(temp_dirs["home"], ".config", "devenv", "hooks", "post_create")
        project_hook = os.path.join(temp_dirs["project"], ".devenv", "hooks", "post_create")
        
        create_hook_script(user_hook, "#!/bin/bash\necho 'user hook'")
        create_hook_script(project_hook, "#!/bin/bash\necho 'project hook'")
        
        hooks = find_hook_scripts("post_create", temp_dirs["project"], temp_dirs["home"])
        assert len(hooks) == 2
        assert hooks[0] == ("user", user_hook)
        assert hooks[1] == ("project", project_hook)
    
    def test_ignore_non_executable_hooks(self, temp_dirs):
        """Test that non-executable files are ignored."""
        user_hook = os.path.join(temp_dirs["home"], ".config", "devenv", "hooks", "post_create")
        create_hook_script(user_hook, "#!/bin/bash\necho 'user hook'", executable=False)
        
        hooks = find_hook_scripts("post_create", temp_dirs["project"], temp_dirs["home"])
        assert hooks == []
    
    def test_different_hook_names(self, temp_dirs):
        """Test finding hooks with different names."""
        post_create_hook = os.path.join(temp_dirs["project"], ".devenv", "hooks", "post_create")
        post_start_hook = os.path.join(temp_dirs["project"], ".devenv", "hooks", "post_start")
        
        create_hook_script(post_create_hook, "#!/bin/bash\necho 'post_create'")
        create_hook_script(post_start_hook, "#!/bin/bash\necho 'post_start'")
        
        create_hooks = find_hook_scripts("post_create", temp_dirs["project"], temp_dirs["home"])
        start_hooks = find_hook_scripts("post_start", temp_dirs["project"], temp_dirs["home"])
        
        assert len(create_hooks) == 1
        assert len(start_hooks) == 1
        assert create_hooks[0][1] == post_create_hook
        assert start_hooks[0][1] == post_start_hook


class TestExecutableCheck:
    """Test executable file detection."""
    
    def test_is_executable_true(self, temp_dirs):
        """Test executable file detection."""
        script_path = os.path.join(temp_dirs["temp"], "test_script")
        create_hook_script(script_path, "#!/bin/bash\necho 'test'", executable=True)
        
        assert is_executable(script_path) is True
    
    def test_is_executable_false(self, temp_dirs):
        """Test non-executable file detection."""
        script_path = os.path.join(temp_dirs["temp"], "test_script")
        create_hook_script(script_path, "#!/bin/bash\necho 'test'", executable=False)
        
        assert is_executable(script_path) is False
    
    def test_is_executable_missing_file(self):
        """Test handling of missing files."""
        assert is_executable("/nonexistent/file") is False


class TestHookCommandGeneration:
    """Test hook command generation."""
    
    def test_generate_hook_command_no_hooks(self, temp_dirs):
        """Test command generation when no hooks exist."""
        command = generate_hook_command("post_create", temp_dirs["project"], temp_dirs["home"])
        assert command is None
    
    def test_generate_hook_command_single_hook(self, temp_dirs):
        """Test command generation with single hook."""
        hook_path = os.path.join(temp_dirs["project"], ".devenv", "hooks", "post_create")
        create_hook_script(hook_path, "#!/bin/bash\necho 'project hook'")
        
        command = generate_hook_command("post_create", temp_dirs["project"], temp_dirs["home"])
        
        assert command is not None
        assert "echo \"Executing project post_create hook...\"" in command
        assert f"source ~/.bashrc && {hook_path}" in command
    
    def test_generate_hook_command_multiple_hooks(self, temp_dirs):
        """Test command generation with multiple hooks."""
        user_hook = os.path.join(temp_dirs["home"], ".config", "devenv", "hooks", "post_create")
        project_hook = os.path.join(temp_dirs["project"], ".devenv", "hooks", "post_create")
        
        create_hook_script(user_hook, "#!/bin/bash\necho 'user hook'")
        create_hook_script(project_hook, "#!/bin/bash\necho 'project hook'")
        
        command = generate_hook_command("post_create", temp_dirs["project"], temp_dirs["home"])
        
        assert command is not None
        assert "Executing user post_create hook" in command
        assert "Executing project post_create hook" in command
        assert f"source ~/.bashrc && {user_hook}" in command
        assert f"source ~/.bashrc && {project_hook}" in command
        assert command.count(" && ") >= 3  # Multiple commands joined


class TestPostCreateCommandGeneration:
    """Test post_create command generation with hooks."""
    
    def test_basic_post_create_command(self, temp_dirs):
        """Test basic post_create command without hooks or user commands."""
        config = {"name": "test-project", "image": "ubuntu:24.04"}
        
        command = generate_post_create_command_with_hooks(config, temp_dirs["project"], temp_dirs["home"])
        
        # Verify mise setup steps are present
        assert "mkdir -p ~/.local/bin" in command
        assert "curl -fsSL https://mise.run | sh" in command
        assert 'eval "$(~/.local/bin/mise activate bash)"' in command
        assert "~/.local/bin/mise install || true" in command
    
    def test_post_create_command_with_dotfiles(self, temp_dirs):
        """Test post_create command with dotfiles configuration."""
        config = {
            "name": "test-project", 
            "image": "ubuntu:24.04",
            "dotfiles_dir": "/tmp/dotfiles"
        }
        
        command = generate_post_create_command_with_hooks(config, temp_dirs["project"], temp_dirs["home"])
        
        assert "cp -r /tmp/devenv-dotfiles/. ~/" in command
    
    def test_post_create_command_with_user_command(self, temp_dirs):
        """Test post_create command with user-specified command."""
        config = {
            "name": "test-project",
            "image": "ubuntu:24.04", 
            "post_create_command": "npm install"
        }
        
        command = generate_post_create_command_with_hooks(config, temp_dirs["project"], temp_dirs["home"])
        
        assert "source ~/.bashrc && npm install" in command
    
    def test_post_create_command_with_hooks(self, temp_dirs):
        """Test post_create command with discovered hooks."""
        config = {"name": "test-project", "image": "ubuntu:24.04"}
        
        # Create a hook
        hook_path = os.path.join(temp_dirs["project"], ".devenv", "hooks", "post_create")
        create_hook_script(hook_path, "#!/bin/bash\nnpm install")
        
        command = generate_post_create_command_with_hooks(config, temp_dirs["project"], temp_dirs["home"])
        
        # Verify hook is executed after mise setup
        assert "~/.local/bin/mise install || true" in command
        assert "Executing project post_create hook" in command
        assert f"source ~/.bashrc && {hook_path}" in command
    
    def test_post_create_command_execution_order(self, temp_dirs):
        """Test that post_create command executes in correct order."""
        config = {
            "name": "test-project",
            "image": "ubuntu:24.04",
            "dotfiles_dir": "/tmp/dotfiles",
            "post_create_command": "npm install"
        }
        
        # Create a hook
        hook_path = os.path.join(temp_dirs["project"], ".devenv", "hooks", "post_create")
        create_hook_script(hook_path, "#!/bin/bash\necho 'hook executed'")
        
        command = generate_post_create_command_with_hooks(config, temp_dirs["project"], temp_dirs["home"])
        
        # Split command and verify order
        parts = command.split(" && ")
        
        # Find indices of key operations
        mise_install_idx = next(i for i, part in enumerate(parts) if "mise install" in part)
        dotfiles_idx = next(i for i, part in enumerate(parts) if "cp -r /tmp/devenv-dotfiles" in part)
        hook_idx = next(i for i, part in enumerate(parts) if hook_path in part)
        user_cmd_idx = next(i for i, part in enumerate(parts) if "npm install" in part)
        
        # Verify execution order: mise -> dotfiles -> hooks -> user command
        assert mise_install_idx < dotfiles_idx
        assert dotfiles_idx < hook_idx
        assert hook_idx < user_cmd_idx


class TestPostStartCommandGeneration:
    """Test post_start command generation with hooks."""
    
    def test_post_start_command_no_hooks_no_config(self, temp_dirs):
        """Test post_start command when nothing is configured."""
        config = {"name": "test-project", "image": "ubuntu:24.04"}
        
        command = generate_post_start_command_with_hooks(config, temp_dirs["project"], temp_dirs["home"])
        
        assert command is None
    
    def test_post_start_command_with_user_command_only(self, temp_dirs):
        """Test post_start command with only user-specified command."""
        config = {
            "name": "test-project",
            "image": "ubuntu:24.04",
            "post_start_command": "npm run dev"
        }
        
        command = generate_post_start_command_with_hooks(config, temp_dirs["project"], temp_dirs["home"])
        
        assert command == "source ~/.bashrc && npm run dev"
    
    def test_post_start_command_with_hooks_only(self, temp_dirs):
        """Test post_start command with only hooks."""
        config = {"name": "test-project", "image": "ubuntu:24.04"}
        
        # Create a hook
        hook_path = os.path.join(temp_dirs["project"], ".devenv", "hooks", "post_start")
        create_hook_script(hook_path, "#!/bin/bash\nnpm run dev")
        
        command = generate_post_start_command_with_hooks(config, temp_dirs["project"], temp_dirs["home"])
        
        assert "Executing project post_start hook" in command
        assert f"source ~/.bashrc && {hook_path}" in command
    
    def test_post_start_command_with_both(self, temp_dirs):
        """Test post_start command with both hooks and user command."""
        config = {
            "name": "test-project",
            "image": "ubuntu:24.04",
            "post_start_command": "echo 'started'"
        }
        
        # Create a hook
        hook_path = os.path.join(temp_dirs["project"], ".devenv", "hooks", "post_start")
        create_hook_script(hook_path, "#!/bin/bash\nnpm run dev")
        
        command = generate_post_start_command_with_hooks(config, temp_dirs["project"], temp_dirs["home"])
        
        assert "Executing project post_start hook" in command
        assert f"source ~/.bashrc && {hook_path}" in command
        assert "source ~/.bashrc && echo 'started'" in command


class TestMiseContextIntegration:
    """Test that hooks execute with proper mise environment context."""
    
    def test_hook_commands_source_bashrc(self, temp_dirs):
        """Test that all hook commands source ~/.bashrc for mise activation."""
        # Create hooks
        user_hook = os.path.join(temp_dirs["home"], ".config", "devenv", "hooks", "post_create")
        project_hook = os.path.join(temp_dirs["project"], ".devenv", "hooks", "post_create")
        
        create_hook_script(user_hook, "#!/bin/bash\nnode --version")
        create_hook_script(project_hook, "#!/bin/bash\npython --version")
        
        command = generate_hook_command("post_create", temp_dirs["project"], temp_dirs["home"])
        
        # Each hook should be prefixed with source ~/.bashrc
        assert command.count("source ~/.bashrc") == 2
        assert f"source ~/.bashrc && {user_hook}" in command
        assert f"source ~/.bashrc && {project_hook}" in command
    
    def test_user_commands_source_bashrc(self, temp_dirs):
        """Test that user-specified commands also source ~/.bashrc."""
        config = {
            "name": "test-project",
            "image": "ubuntu:24.04",
            "post_create_command": "npm install",
            "post_start_command": "npm run dev"
        }
        
        create_command = generate_post_create_command_with_hooks(config, temp_dirs["project"], temp_dirs["home"])
        start_command = generate_post_start_command_with_hooks(config, temp_dirs["project"], temp_dirs["home"])
        
        assert "source ~/.bashrc && npm install" in create_command
        assert start_command == "source ~/.bashrc && npm run dev"


# Integration test would require a more complex setup with actual devcontainer
class TestHookSystemIntegration:
    """Test integration with devcontainer generation."""
    
    def test_hooks_integrated_into_devcontainer_json(self, temp_dirs):
        """Test that hooks are properly integrated into devcontainer.json generation."""
        # This would require importing and testing with actual devcontainer.py
        # For now, we verify the hook functions work as expected
        config = {
            "name": "test-project",
            "image": "ubuntu:24.04",
            "post_create_command": "echo 'setup complete'"
        }
        
        # Create hooks
        create_hook = os.path.join(temp_dirs["project"], ".devenv", "hooks", "post_create")
        start_hook = os.path.join(temp_dirs["project"], ".devenv", "hooks", "post_start")
        
        create_hook_script(create_hook, "#!/bin/bash\necho 'create hook'")
        create_hook_script(start_hook, "#!/bin/bash\necho 'start hook'")
        
        # Generate commands
        create_cmd = generate_post_create_command_with_hooks(config, temp_dirs["project"], temp_dirs["home"])
        start_cmd = generate_post_start_command_with_hooks(config, temp_dirs["project"], temp_dirs["home"])
        
        # Verify integration
        assert create_cmd is not None
        assert start_cmd is not None
        assert "mise install" in create_cmd  # Built-in setup
        assert "create hook" in create_cmd   # Hook integration
        assert "setup complete" in create_cmd  # User command
        assert "start hook" in start_cmd     # Hook integration