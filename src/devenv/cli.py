"""
CLI command definitions for devenv
"""

import click
from . import __version__


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
def list():
    """List all managed dev containers"""
    click.echo("devenv list - not implemented yet")


if __name__ == "__main__":
    cli()