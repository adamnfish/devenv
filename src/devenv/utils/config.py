"""
Configuration loading and management utilities
"""

import os
import yaml
from pathlib import Path
from typing import Dict, Optional


def generate_default_config(project_path: str = ".", ports: Optional[list] = None) -> Dict[str, any]:
    """
    Generate default devenv configuration with sensible defaults.
    Uses vanilla Ubuntu LTS since mise handles toolchain management.
    
    Args:
        project_path: Path to the project directory
        ports: Optional list of port mappings (e.g., ["3000:3000", "5432:5432"])
        
    Returns:
        Dictionary with default project settings
    """
    project_path = Path(project_path)
    
    config = {
        "name": project_path.resolve().name,
        "image": "mcr.microsoft.com/devcontainers/base:ubuntu-24.04"
    }
    
    # Only include ports if explicitly provided
    if ports:
        config["ports"] = ports
    
    return config


def load_project_config(config_path: str = ".devenv/config.yml") -> Optional[Dict[str, any]]:
    """
    Load existing project configuration if it exists.
    
    Args:
        config_path: Path to the config file
        
    Returns:
        Configuration dictionary or None if file doesn't exist
    """
    config_file = Path(config_path)
    
    if not config_file.exists():
        return None
    
    with open(config_file, 'r') as f:
        return yaml.safe_load(f)


def write_config_file(config: Dict[str, any], config_path: str = ".devenv/config.yml"):
    """
    Write configuration to YAML file.
    
    Args:
        config: Configuration dictionary
        config_path: Path where to write the config file
    """
    config_file = Path(config_path)
    
    # Create directory if it doesn't exist
    config_file.parent.mkdir(parents=True, exist_ok=True)
    
    with open(config_file, 'w') as f:
        yaml.dump(config, f, default_flow_style=False, sort_keys=False)


def load_user_config(home_dir: Optional[str] = None) -> Optional[Dict[str, any]]:
    """
    Load user configuration from ~/.config/devenv/ directory.
    User config contains personal preferences (IDE plugins, hooks, dotfiles),
    NOT application-specific settings like images or ports.
    
    Args:
        home_dir: Override home directory path (for testing)
    
    Returns:
        User configuration dictionary or None if no user config found
    """
    if home_dir:
        user_config_dir = Path(home_dir) / ".config" / "devenv"
    else:
        user_config_dir = Path.home() / ".config" / "devenv"
    
    if not user_config_dir.exists():
        return None
    
    user_config = {}
    
    # Load IDE plugin preferences
    try:
        vscode_plugins_file = user_config_dir / "plugins.vscode.txt"
        if vscode_plugins_file.exists():
            with open(vscode_plugins_file, 'r') as f:
                plugins = [line.strip() for line in f if line.strip() and not line.startswith('#')]
                if plugins:
                    user_config.setdefault('plugins', {})['vscode'] = plugins
        
        jetbrains_plugins_file = user_config_dir / "plugins.jetbrains.txt"
        if jetbrains_plugins_file.exists():
            with open(jetbrains_plugins_file, 'r') as f:
                plugins = [line.strip() for line in f if line.strip() and not line.startswith('#')]
                if plugins:
                    user_config.setdefault('plugins', {})['jetbrains'] = plugins
        
        # Check for hooks directory
        hooks_dir = user_config_dir / "hooks"
        if hooks_dir.exists():
            user_config['hooks_dir'] = str(hooks_dir)
        
        # Check for dotfiles directory
        dotfiles_dir = user_config_dir / "dotfiles"
        if dotfiles_dir.exists():
            user_config['dotfiles_dir'] = str(dotfiles_dir)
            
    except Exception as e:
        print(f"Warning: Could not load user config from {user_config_dir}: {e}")
        return None
    
    return user_config if user_config else None


def merge_configs(project_config: Optional[Dict], user_config: Optional[Dict], project_path: str = ".") -> Dict[str, any]:
    """
    Merge project and user configurations with proper separation of concerns.
    
    Project config: Application-specific settings (image, ports, environment)
    User config: Personal preferences (IDE plugins, hooks, dotfiles)
    
    Args:
        project_config: Project configuration dictionary (can be None)
        user_config: User configuration dictionary (can be None)
        project_path: Path to the project directory (for default name)
        
    Returns:
        Merged configuration dictionary
    """
    # Start with built-in defaults for application settings
    merged = generate_default_config(project_path)
    
    # Apply project config - this contains application-specific settings
    if project_config:
        merged.update(project_config)
    
    # Apply user config - only for personal preferences, not application settings
    if user_config:
        # Merge IDE plugin preferences
        if 'plugins' in user_config:
            project_plugins = merged.get('plugins', {})
            user_plugins = user_config['plugins']
            
            # Merge plugins by IDE type
            for ide_type, plugins in user_plugins.items():
                if ide_type in project_plugins:
                    # Combine project and user plugins, removing duplicates
                    combined_plugins = list(set(project_plugins[ide_type] + plugins))
                    merged.setdefault('plugins', {})[ide_type] = combined_plugins
                else:
                    merged.setdefault('plugins', {})[ide_type] = plugins
        
        # Add user-specific paths for hooks and dotfiles
        for key in ['hooks_dir', 'dotfiles_dir']:
            if key in user_config:
                merged[key] = user_config[key]
    
    return merged


def validate_config(config: Dict[str, any]) -> Dict[str, any]:
    """
    Validate configuration has required fields and sensible values.
    
    Args:
        config: Configuration dictionary to validate
        
    Returns:
        Validated configuration dictionary
        
    Raises:
        ValueError: If required fields are missing or invalid
    """
    required_fields = ["name", "image"]
    
    for field in required_fields:
        if field not in config or not config[field]:
            raise ValueError(f"Required field '{field}' is missing or empty in configuration")
    
    # Validate name is filesystem-safe
    if not config["name"].replace("-", "").replace("_", "").isalnum():
        raise ValueError(f"Project name '{config['name']}' contains invalid characters. Use only alphanumeric, hyphens, and underscores.")
    
    return config


def load_and_merge_config(project_path: str = ".", home_dir: Optional[str] = None) -> Dict[str, any]:
    """
    Load project and user configs, merge them, and validate the result.
    This is the main function for loading configuration for devenv commands.
    
    Args:
        project_path: Path to the project directory
        home_dir: Override home directory path (for testing)
        
    Returns:
        Merged and validated configuration dictionary
        
    Raises:
        ValueError: If configuration is invalid
        FileNotFoundError: If project config is required but missing
    """
    # Load project config
    project_config = load_project_config(f"{project_path}/.devenv/config.yml")
    
    # Load user config
    user_config = load_user_config(home_dir)
    
    # Merge configurations
    merged_config = merge_configs(project_config, user_config, project_path)
    
    # Validate the result
    return validate_config(merged_config)


def config_exists(config_path: str = ".devenv/config.yml") -> bool:
    """
    Check if configuration file already exists.
    
    Args:
        config_path: Path to the config file
        
    Returns:
        True if config file exists, False otherwise
    """
    return Path(config_path).exists()