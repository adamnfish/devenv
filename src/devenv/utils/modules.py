"""
Built-in module definitions and management
"""

from typing import Dict, List, Any


# Built-in module definitions
BUILTIN_MODULES = {
    "claude-code": {
        "description": "Integrates Claude Code for AI-assisted development",
        "mounts": [
            {
                "source": "${localEnv:HOME}/.claude",
                "target": "/home/vscode/.claude",
                "type": "bind"
            }
        ],
        "environment": {
            "CLAUDE_CODE_ENABLED": "true"
        },
        "post_create_commands": [
            "npm install -g @anthropic/claude-code"
        ]
    },
    
    
    "docker-in-docker": {
        "description": "Allows running Docker commands inside the container",
        "features": {
            "ghcr.io/devcontainers/features/docker-in-docker:2": {
                "moby": True,
                "dockerDashComposeVersion": "v2"
            }
        },
        "mounts": [
            {
                "source": "/var/run/docker.sock",
                "target": "/var/run/docker-host.sock",
                "type": "bind"
            }
        ]
    }
}


def get_available_modules() -> Dict[str, Dict[str, Any]]:
    """
    Get all available built-in modules.
    
    Returns:
        Dictionary of module names to module definitions
    """
    return BUILTIN_MODULES.copy()


def get_module(name: str) -> Dict[str, Any]:
    """
    Get a specific module definition by name.
    
    Args:
        name: Module name
        
    Returns:
        Module definition dictionary
        
    Raises:
        KeyError: If module doesn't exist
    """
    if name not in BUILTIN_MODULES:
        available = list(BUILTIN_MODULES.keys())
        raise KeyError(f"Module '{name}' not found. Available modules: {', '.join(available)}")
    
    return BUILTIN_MODULES[name].copy()


def validate_modules(module_names: List[str]) -> List[str]:
    """
    Validate that all requested modules exist.
    
    Args:
        module_names: List of module names to validate
        
    Returns:
        List of validated module names
        
    Raises:
        ValueError: If any module doesn't exist
    """
    available_modules = set(BUILTIN_MODULES.keys())
    invalid_modules = [name for name in module_names if name not in available_modules]
    
    if invalid_modules:
        available = sorted(list(available_modules))
        raise ValueError(
            f"Unknown modules: {', '.join(invalid_modules)}. "
            f"Available modules: {', '.join(available)}"
        )
    
    return module_names


def apply_modules_to_devcontainer(devcontainer_config: Dict[str, Any], module_names: List[str]) -> Dict[str, Any]:
    """
    Apply module configurations to devcontainer.json.
    
    Args:
        devcontainer_config: Base devcontainer.json configuration
        module_names: List of module names to apply
        
    Returns:
        Updated devcontainer.json configuration
    """
    if not module_names:
        return devcontainer_config
    
    # Validate modules first
    validate_modules(module_names)
    
    # Apply each module
    for module_name in module_names:
        module = get_module(module_name)
        devcontainer_config = _merge_module_config(devcontainer_config, module)
    
    return devcontainer_config


def _merge_module_config(devcontainer_config: Dict[str, Any], module_config: Dict[str, Any]) -> Dict[str, Any]:
    """
    Merge a single module configuration into devcontainer.json.
    
    Args:
        devcontainer_config: Base devcontainer configuration
        module_config: Module configuration to merge
        
    Returns:
        Updated devcontainer configuration
    """
    config = devcontainer_config.copy()
    
    # Merge run arguments
    if "run_args" in module_config:
        current_args = config.get("runArgs", [])
        config["runArgs"] = current_args + module_config["run_args"]
    
    # Merge mounts
    if "mounts" in module_config:
        current_mounts = config.get("mounts", [])
        config["mounts"] = current_mounts + module_config["mounts"]
    
    # Merge environment variables
    if "environment" in module_config:
        current_env = config.get("remoteEnv", {})
        current_env.update(module_config["environment"])
        config["remoteEnv"] = current_env
    
    # Merge features
    if "features" in module_config:
        current_features = config.get("features", {})
        current_features.update(module_config["features"])
        config["features"] = current_features
    
    # Merge post-create commands
    if "post_create_commands" in module_config:
        current_command = config.get("postCreateCommand", "")
        module_commands = " && ".join(module_config["post_create_commands"])
        
        if current_command:
            config["postCreateCommand"] = f"{current_command} && {module_commands}"
        else:
            config["postCreateCommand"] = module_commands
    
    return config


def list_modules() -> None:
    """Print available modules and their descriptions."""
    modules = get_available_modules()
    
    if not modules:
        print("No built-in modules available")
        return
    
    print("Available built-in modules:")
    print()
    
    for name, config in modules.items():
        description = config.get("description", "No description available")
        print(f"  {name}")
        print(f"    {description}")
        print()


def get_module_summary(module_name: str) -> str:
    """
    Get a brief summary of what a module does.
    
    Args:
        module_name: Name of the module
        
    Returns:
        Brief description string
    """
    try:
        module = get_module(module_name)
        return module.get("description", f"Module: {module_name}")
    except KeyError:
        return f"Unknown module: {module_name}"