"""
CLI helper utilities for consistent error handling and messaging
"""

import click
import functools
from .docker import DockerNotAvailableError, is_docker_available


def handle_docker_errors(func):
    """Decorator to handle Docker errors consistently across CLI commands"""
    @functools.wraps(func)
    def wrapper(*args, **kwargs):
        try:
            if not is_docker_available():
                click.echo("Error: Docker is not running or not available", err=True)
                click.echo("Please start Docker and try again", err=True)
                raise click.Abort()
            
            return func(*args, **kwargs)
        except DockerNotAvailableError as e:
            click.echo(f"Error: {e}", err=True)
            raise click.Abort()
        except Exception as e:
            click.echo(f"Unexpected error: {e}", err=True)
            raise click.Abort()
    
    return wrapper


def check_docker_available():
    """Check if Docker is available and raise appropriate error if not"""
    if not is_docker_available():
        click.echo("Error: Docker is not running or not available", err=True)
        click.echo("Please start Docker and try again", err=True)
        raise click.Abort()