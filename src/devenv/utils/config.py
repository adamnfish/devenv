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


def config_exists(config_path: str = ".devenv/config.yml") -> bool:
    """
    Check if configuration file already exists.
    
    Args:
        config_path: Path to the config file
        
    Returns:
        True if config file exists, False otherwise
    """
    return Path(config_path).exists()