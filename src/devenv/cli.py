"""
CLI command definitions for devenv
"""

import click
from . import __version__
from .utils.docker import find_devenv_containers
from .utils.cli_helpers import handle_docker_errors
from .utils.config import generate_default_config, write_config_file, config_exists


@click.group()
@click.version_option(version=__version__)
@click.pass_context
def cli(ctx):
    """devenv - CLI tool for managing ephemeral, branch-scoped Dev Container environments"""
    pass


@cli.command()
@click.option('--force', '-f', is_flag=True, help='Overwrite existing config file')
@click.option('--port', '-p', help='Port mapping (e.g., 3000:3000)')
def init(force, port):
    """Initialize a new devenv project"""
    config_path = ".devenv/config.yml"
    
    # Check if config already exists
    if config_exists(config_path) and not force:
        if not click.confirm("Configuration file already exists. Overwrite?"):
            click.echo("Initialization cancelled.")
            return
    
    try:
        # Generate default config with optional port
        port_list = [port] if port else None
        config = generate_default_config(ports=port_list)
        
        # Write config file
        write_config_file(config, config_path)
        
        click.echo(f"✓ Initialized devenv project: {config['name']}")
        click.echo(f"  Image: {config['image']}")
        if 'ports' in config:
            click.echo(f"  Ports: {config['ports']}")
        else:
            click.echo(f"  Ports: none")
        click.echo(f"  Config: {config_path}")
        click.echo("\nNext: Run 'devenv create <branch>' to start developing")
        
    except Exception as e:
        click.echo(f"Error initializing project: {e}", err=True)
        exit(1)


@cli.command()
@click.argument('branch')
def create(branch):
    """Create a new dev container for the specified branch"""
    click.echo(f"devenv create {branch} - not implemented yet")


@cli.command()
@handle_docker_errors
def list():
    """List all managed dev containers"""
    containers = find_devenv_containers()
    if not containers:
        click.echo("No devenv containers found")
    else:
        click.echo(f"Found {len(containers)} devenv containers")
        for container in containers:
            click.echo(f"- {container.name} ({container.status})")
