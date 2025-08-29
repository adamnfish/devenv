"""
CLI command definitions for devenv
"""

import click
from . import __version__
from .utils.docker import find_devenv_containers
from .utils.cli_helpers import handle_docker_errors


@click.group()
@click.version_option(version=__version__)
@click.pass_context
def cli(ctx):
    """devenv - CLI tool for managing ephemeral, branch-scoped Dev Container environments"""
    pass


@cli.command()
def init():
    """Initialize a new devenv project"""
    click.echo("devenv init - not implemented yet")


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


if __name__ == "__main__":
    cli()