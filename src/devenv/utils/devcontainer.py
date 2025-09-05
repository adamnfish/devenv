"""
devcontainer.json generation utilities
"""

import json
import tempfile
import os
from pathlib import Path
from typing import Dict, List, Optional, Any
from contextlib import contextmanager
from datetime import datetime

from .config import load_and_merge_config
from .modules import apply_modules_to_devcontainer, validate_modules


def extract_ports_from_config(ports: Optional[List[str]]) -> List[int]:
    """
    Extract port numbers from port mapping strings.
    
    Args:
        ports: List of port mappings like ["3000:3000", "5432:5432"]
        
    Returns:
        List of port numbers to forward
    """
    if not ports:
        return []
    
    forwarded_ports = []
    for port_mapping in ports:
        if ":" in port_mapping:
            # Extract the host port (left side of :)
            host_port = port_mapping.split(":")[0]
            try:
                forwarded_ports.append(int(host_port))
            except ValueError:
                # Skip invalid port mappings
                continue
        else:
            # Single port number
            try:
                forwarded_ports.append(int(port_mapping))
            except ValueError:
                continue
    
    return forwarded_ports


def generate_docker_labels(branch: str, repo: str, repo_path: str, editor: str = "vscode", modules: Optional[List[str]] = None) -> List[str]:
    """
    Generate Docker labels for container identification and metadata.
    
    Args:
        branch: Git branch name
        repo: Repository name
        repo_path: Full path to repository
        editor: IDE type (vscode or jetbrains)
        modules: List of active modules
        
    Returns:
        List of --label arguments for Docker
    """
    labels = [
        "--label=com.devenv.managed=true",
        f"--label=com.devenv.repo={repo}",
        f"--label=com.devenv.repo-path={repo_path}",
        f"--label=com.devenv.branch={branch}",
        f"--label=com.devenv.editor={editor}",
        f"--label=com.devenv.created={datetime.utcnow().isoformat()}Z",
        "--label=com.devenv.tainted=false",
    ]
    
    if modules:
        labels.append(f"--label=com.devenv.modules={','.join(modules)}")
    else:
        labels.append("--label=com.devenv.modules=")
    
    return labels


def generate_devcontainer_json(merged_config: Dict[str, Any], branch: str, repo: str, repo_path: str, editor: str = "vscode", modules: Optional[List[str]] = None) -> Dict[str, Any]:
    """
    Generate devcontainer.json from merged configuration.
    
    Args:
        merged_config: Merged configuration dictionary
        branch: Git branch name
        repo: Repository name  
        repo_path: Full path to repository
        editor: IDE type (vscode or jetbrains)
        modules: List of active modules
        
    Returns:
        devcontainer.json dictionary
    """
    # Extract basic settings
    container_name = f"devenv-{repo}-{branch}-{editor}"
    forwarded_ports = extract_ports_from_config(merged_config.get("ports"))
    
    # Base devcontainer structure
    devcontainer = {
        "name": container_name,
        "image": merged_config["image"]
    }
    
    # Add port forwarding if any ports specified
    if forwarded_ports:
        devcontainer["forwardPorts"] = forwarded_ports
    
    # Add environment variables if specified
    if "environment" in merged_config:
        devcontainer["remoteEnv"] = merged_config["environment"]
    
    # Add features if specified
    if "features" in merged_config:
        devcontainer["features"] = merged_config["features"]
    
    # Base mounts always include mise cache for toolchain management
    base_mounts = [
        {
            "source": "${localEnv:HOME}/.local/share/mise",
            "target": "/home/vscode/.local/share/mise", 
            "type": "bind"
        }
    ]
    
    # Add additional mounts from config
    if "mounts" in merged_config:
        base_mounts.extend(merged_config["mounts"])
    
    # Add user dotfiles mount if specified
    if "dotfiles_dir" in merged_config:
        base_mounts.append({
            "source": merged_config["dotfiles_dir"],
            "target": "/tmp/devenv-dotfiles",
            "type": "bind"
        })
    
    devcontainer["mounts"] = base_mounts
    
    # Add IDE customizations
    customizations = {}
    if "plugins" in merged_config:
        if editor == "vscode" and "vscode" in merged_config["plugins"]:
            customizations["vscode"] = {
                "extensions": merged_config["plugins"]["vscode"]
            }
        elif editor == "jetbrains" and "jetbrains" in merged_config["plugins"]:
            customizations["jetbrains"] = {
                "plugins": merged_config["plugins"]["jetbrains"]
            }
    
    if customizations:
        devcontainer["customizations"] = customizations
    
    # Generate post-create command including mise setup
    post_create_commands = []
    
    # Install mise
    post_create_commands.append("curl -fsSL https://mise.run | sh")
    
    # Setup shell activation
    post_create_commands.append('echo \'eval "$(~/.local/bin/mise activate bash)"\' >> ~/.bashrc')
    post_create_commands.append('echo \'eval "$(~/.local/bin/mise activate zsh)"\' >> ~/.zshrc')
    
    # Install tools from project's mise config files
    post_create_commands.append("~/.local/bin/mise install")
    
    # Copy dotfiles if directory was mounted
    if "dotfiles_dir" in merged_config:
        post_create_commands.append("cp -r /tmp/devenv-dotfiles/. ~/")
    
    # Add user's post_create_command if specified
    if "post_create_command" in merged_config:
        post_create_commands.append(merged_config["post_create_command"])
    
    devcontainer["postCreateCommand"] = " && ".join(post_create_commands)
    
    # Add post-start command if specified
    if "post_start_command" in merged_config:
        devcontainer["postStartCommand"] = merged_config["post_start_command"]
    
    # Add Docker labels for container identification
    docker_labels = generate_docker_labels(branch, repo, repo_path, editor, modules)
    devcontainer["runArgs"] = docker_labels
    
    # Apply modules to devcontainer configuration
    if modules:
        devcontainer = apply_modules_to_devcontainer(devcontainer, modules)
    
    return devcontainer


@contextmanager
def temp_devcontainer_json(devcontainer_config: Dict[str, Any]):
    """
    Create temporary devcontainer.json file, yield path, then cleanup.
    
    Args:
        devcontainer_config: devcontainer.json dictionary
        
    Yields:
        Path to temporary devcontainer.json file
    """
    with tempfile.TemporaryDirectory() as tmpdir:
        devcontainer_path = os.path.join(tmpdir, "devcontainer.json")
        with open(devcontainer_path, 'w') as f:
            json.dump(devcontainer_config, f, indent=2)
        yield devcontainer_path


def create_devcontainer_from_config(project_path: str = ".", branch: str = "main", editor: str = "vscode", modules: Optional[List[str]] = None, home_dir: Optional[str] = None):
    """
    Create devcontainer.json from project configuration.
    This is the main function used by devenv create command.
    
    Args:
        project_path: Path to project directory
        branch: Git branch name
        editor: IDE type (vscode or jetbrains)
        modules: List of active modules
        home_dir: Override home directory (for testing)
        
    Returns:
        Context manager yielding path to temporary devcontainer.json
    """
    # Load and merge configuration
    merged_config = load_and_merge_config(project_path, home_dir)
    
    # Extract repository information
    repo = os.path.basename(os.path.abspath(project_path))
    repo_path = os.path.abspath(project_path)
    
    # Generate devcontainer.json
    devcontainer_config = generate_devcontainer_json(
        merged_config, branch, repo, repo_path, editor, modules
    )
    
    # Return context manager for temporary file
    return temp_devcontainer_json(devcontainer_config)


def get_container_name(repo: str, branch: str, editor: str = "vscode") -> str:
    """
    Generate standardized container name.
    
    Args:
        repo: Repository name
        branch: Git branch name
        editor: IDE type
        
    Returns:
        Container name string
    """
    return f"devenv-{repo}-{branch}-{editor}"