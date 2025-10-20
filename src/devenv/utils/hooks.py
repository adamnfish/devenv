"""
Hook system for devenv container lifecycle events.

Provides hook discovery, validation, and execution integration
with mise-managed toolchain environments.
"""

import os
from pathlib import Path
from typing import List, Optional, Tuple
import stat


def find_hook_scripts(hook_name: str, project_path: str = ".", home_dir: Optional[str] = None) -> List[Tuple[str, str]]:
    """
    Discover hook scripts for a given hook name.
    
    Args:
        hook_name: Hook name (e.g., 'post_create', 'post_start')
        project_path: Path to project directory
        home_dir: Override home directory (for testing)
        
    Returns:
        List of (source, script_path) tuples in execution order:
        - ('user', '/home/user/.config/devenv/hooks/post_create')
        - ('project', '/project/.devenv/hooks/post_create')
    """
    hooks = []
    
    # Determine home directory
    if home_dir is None:
        home_dir = os.path.expanduser("~")
    
    # Check user-level hook
    user_hook_path = os.path.join(home_dir, ".config", "devenv", "hooks", hook_name)
    if os.path.isfile(user_hook_path) and is_executable(user_hook_path):
        hooks.append(("user", user_hook_path))
    
    # Check project-level hook (runs after user hook)
    project_hook_path = os.path.join(project_path, ".devenv", "hooks", hook_name)
    if os.path.isfile(project_hook_path) and is_executable(project_hook_path):
        hooks.append(("project", project_hook_path))
    
    return hooks


def is_executable(file_path: str) -> bool:
    """
    Check if a file is executable.
    
    Args:
        file_path: Path to file
        
    Returns:
        True if file is executable
    """
    try:
        file_stat = os.stat(file_path)
        return bool(file_stat.st_mode & stat.S_IEXEC)
    except (OSError, FileNotFoundError):
        return False


def generate_hook_command(hook_name: str, project_path: str = ".", home_dir: Optional[str] = None) -> Optional[str]:
    """
    Generate shell command to execute all discovered hooks for a given hook name.
    
    Hook scripts are executed with mise environment activated, ensuring
    access to project-specified tool versions.
    
    Args:
        hook_name: Hook name (e.g., 'post_create', 'post_start')
        project_path: Path to project directory
        home_dir: Override home directory (for testing)
        
    Returns:
        Shell command string to execute hooks, or None if no hooks found
    """
    hook_scripts = find_hook_scripts(hook_name, project_path, home_dir)
    
    if not hook_scripts:
        return None
    
    # Build command that executes hooks with mise environment
    commands = []
    
    for source, script_path in hook_scripts:
        # Execute hook with mise environment activated
        # Use source ~/.bashrc to ensure mise is in PATH, then execute hook
        hook_command = f'''
        echo "Executing {source} {hook_name} hook..."
        source ~/.bashrc && {script_path}
        '''
        commands.append(hook_command.strip())
    
    return " && ".join(commands)


def generate_post_create_command_with_hooks(merged_config: dict, project_path: str = ".", home_dir: Optional[str] = None) -> str:
    """
    Generate complete post_create_command including mise setup, hooks, and user commands.
    
    Execution order:
    1. Setup directories and install mise
    2. Activate mise for bash/zsh shells  
    3. Install project tools via mise
    4. Copy dotfiles if specified
    5. Execute discovered post_create hooks (with mise context)
    6. Execute user's post_create_command if specified
    
    Args:
        merged_config: Merged configuration dictionary
        project_path: Path to project directory
        home_dir: Override home directory (for testing)
        
    Returns:
        Complete shell command string for postCreateCommand
    """
    commands = []
    
    # 1. Create directories and install mise
    commands.append("mkdir -p ~/.local/bin")
    commands.append("curl -fsSL https://mise.run | sh")
    
    # 2. Setup shell activation
    commands.append('echo \'eval "$(~/.local/bin/mise activate bash)"\' >> ~/.bashrc')
    commands.append('echo \'eval "$(~/.local/bin/mise activate zsh)"\' >> ~/.zshrc')
    
    # 3. Install tools from project's mise config files
    commands.append("~/.local/bin/mise install || true")
    
    # 4. Copy dotfiles if directory was mounted
    if "dotfiles_dir" in merged_config:
        commands.append("cp -r /tmp/devenv-dotfiles/. ~/")
    
    # 5. Execute post_create hooks with mise context
    hook_command = generate_hook_command("post_create", project_path, home_dir)
    if hook_command:
        commands.append(hook_command)
    
    # 6. Execute user's post_create_command if specified
    if "post_create_command" in merged_config:
        commands.append(f"source ~/.bashrc && {merged_config['post_create_command']}")
    
    return " && ".join(commands)


def generate_post_start_command_with_hooks(merged_config: dict, project_path: str = ".", home_dir: Optional[str] = None) -> Optional[str]:
    """
    Generate complete post_start_command including hooks and user commands.
    
    Execution order:
    1. Execute discovered post_start hooks (with mise context)
    2. Execute user's post_start_command if specified
    
    Args:
        merged_config: Merged configuration dictionary  
        project_path: Path to project directory
        home_dir: Override home directory (for testing)
        
    Returns:
        Complete shell command string for postStartCommand, or None if no commands
    """
    commands = []
    
    # 1. Execute post_start hooks with mise context
    hook_command = generate_hook_command("post_start", project_path, home_dir)
    if hook_command:
        commands.append(hook_command)
    
    # 2. Execute user's post_start_command if specified
    if "post_start_command" in merged_config:
        commands.append(f"source ~/.bashrc && {merged_config['post_start_command']}")
    
    return " && ".join(commands) if commands else None


def validate_hook_environment() -> List[str]:
    """
    Validate that the hook execution environment is properly configured.
    
    Returns:
        List of validation errors, empty if all checks pass
    """
    errors = []
    
    # Check if mise is available in PATH
    if not os.access("/home/vscode/.local/bin/mise", os.X_OK):
        errors.append("mise not found at expected location: /home/vscode/.local/bin/mise")
    
    # Check if shell activation is configured
    bashrc_path = "/home/vscode/.bashrc"
    if os.path.exists(bashrc_path):
        with open(bashrc_path, 'r') as f:
            bashrc_content = f.read()
            if 'mise activate' not in bashrc_content:
                errors.append("mise activation not found in ~/.bashrc")
    else:
        errors.append("~/.bashrc not found")
    
    return errors