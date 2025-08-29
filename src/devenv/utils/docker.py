"""
Docker operations and container management utilities
"""

import docker
from docker.errors import DockerException
from typing import List, Dict, Optional


class DockerNotAvailableError(Exception):
    """Raised when Docker is not running or available"""
    pass


def get_docker_client() -> docker.DockerClient:
    """Get Docker client with error handling"""
    try:
        client = docker.from_env()
        # Test connection
        client.ping()
        return client
    except DockerException as e:
        raise DockerNotAvailableError(f"Docker is not available: {e}")


def _safe_docker_operation(operation_name: str, operation_func):
    """Common wrapper for Docker operations with consistent error handling"""
    try:
        return operation_func()
    except DockerNotAvailableError:
        raise
    except Exception as e:
        raise DockerException(f"Failed to {operation_name}: {e}")


def find_devenv_containers() -> List[docker.models.containers.Container]:
    """Find all containers managed by devenv"""
    def _list_containers():
        client = get_docker_client()
        return client.containers.list(
            all=True,
            filters={"label": "com.devenv.managed=true"}
        )
    
    return _safe_docker_operation("list devenv containers", _list_containers)


def find_container_by_branch(branch: str, repo: str) -> Optional[docker.models.containers.Container]:
    """Find container for specific branch and repo"""
    def _find_container():
        client = get_docker_client()
        containers = client.containers.list(
            all=True,
            filters={
                "label": [
                    "com.devenv.managed=true",
                    f"com.devenv.branch={branch}",
                    f"com.devenv.repo={repo}"
                ]
            }
        )
        return containers[0] if containers else None
    
    return _safe_docker_operation(f"find container for branch {branch}", _find_container)


def get_container_labels(container: docker.models.containers.Container) -> Dict[str, str]:
    """Extract devenv-specific labels from container"""
    all_labels = container.labels or {}
    devenv_labels = {}
    
    # Extract only devenv-related labels
    for key, value in all_labels.items():
        if key.startswith("com.devenv."):
            # Remove the com.devenv. prefix for cleaner access
            clean_key = key.replace("com.devenv.", "")
            devenv_labels[clean_key] = value
    
    return devenv_labels


def is_docker_available() -> bool:
    """Check if Docker is available without raising exceptions"""
    try:
        get_docker_client()
        return True
    except DockerNotAvailableError:
        return False