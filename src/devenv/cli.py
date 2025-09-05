"""
CLI command definitions for devenv
"""

import os
import subprocess
import click
from . import __version__
from .utils.docker import find_devenv_containers, find_container_by_branch
from .utils.cli_helpers import handle_docker_errors
from .utils.config import generate_default_config, write_config_file, config_exists, load_and_merge_config
from .utils.devcontainer import create_devcontainer_from_config, get_container_name


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
@click.option('--modules', help='Comma-separated list of modules to enable')
@click.option('--editor', default='vscode', type=click.Choice(['vscode', 'jetbrains']), 
              help='IDE type (default: vscode)')
@click.option('--ports', help='Additional port mappings (comma-separated)')
@handle_docker_errors
def create(branch, modules, editor, ports):
    """Create a new dev container for the specified branch"""
    
    # Check if project config exists
    if not config_exists('.devenv/config.yml'):
        click.echo("Error: No .devenv/config.yml found. Run 'devenv init' first.", err=True)
        exit(1)
    
    # Parse modules list
    modules_list = [m.strip() for m in modules.split(',')] if modules else []
    
    # Get repository name and check for existing container
    repo = os.path.basename(os.path.abspath('.'))
    existing_container = find_container_by_branch(branch, repo)
    
    if existing_container:
        click.echo(f"Container for branch '{branch}' already exists: {existing_container.name}")
        click.echo(f"Use 'devenv switch {branch}' to connect to it.")
        exit(1)
    
    # Check if devcontainer CLI is available
    try:
        subprocess.run(['devcontainer', '--version'], capture_output=True, check=True)
    except (subprocess.CalledProcessError, FileNotFoundError):
        click.echo("Error: devcontainer CLI not found. Install with:", err=True)
        click.echo("  npm install -g @devcontainers/cli", err=True)
        exit(1)
    
    try:
        # Create devcontainer.json from config
        with create_devcontainer_from_config('.', branch, editor, modules_list) as devcontainer_path:
            container_name = get_container_name(repo, branch, editor)
            
            click.echo(f"Creating dev container: {container_name}")
            if modules_list:
                click.echo(f"Enabled modules: {', '.join(modules_list)}")
            
            # Run devcontainer CLI
            cmd = [
                'devcontainer', 'up',
                '--workspace-folder', '.',
                '--config', devcontainer_path
            ]
            
            click.echo("Running devcontainer up...")
            result = subprocess.run(cmd, capture_output=False)
            
            if result.returncode == 0:
                click.echo(f"✓ Container created successfully: {container_name}")
                
                # Launch IDE based on editor choice
                if editor == 'vscode':
                    click.echo("Launching VS Code...")
                    subprocess.run(['code', '.'], capture_output=True)
                else:
                    click.echo(f"Container ready. Connect your {editor} IDE manually.")
                    
            else:
                click.echo("Error: Failed to create dev container", err=True)
                exit(1)
                
    except KeyboardInterrupt:
        click.echo("\nContainer creation cancelled.")
        exit(1)
    except Exception as e:
        click.echo(f"Error creating container: {e}", err=True)
        exit(1)


@cli.command()
@handle_docker_errors
def list():
    """List all managed dev containers"""
    containers = find_devenv_containers()
    
    if not containers:
        click.echo("No devenv containers found")
        return
    
    # Display header
    click.echo(f"{'BRANCH':<20} {'CONTAINER_ID':<12} {'STATUS':<10} {'EDITOR':<10} {'PORTS':<20}")
    click.echo("-" * 82)
    
    for container in containers:
        # Extract information from container labels
        labels = container.attrs.get('Config', {}).get('Labels', {})
        
        branch = labels.get('com.devenv.branch', 'unknown')
        container_id = container.short_id
        status = container.status
        editor = labels.get('com.devenv.editor', 'unknown')
        modules = labels.get('com.devenv.modules', '')
        
        # Extract port mappings from container
        ports = []
        if container.attrs.get('NetworkSettings', {}).get('Ports'):
            for port, mappings in container.attrs['NetworkSettings']['Ports'].items():
                if mappings:
                    host_port = mappings[0]['HostPort']
                    ports.append(host_port)
        
        ports_str = ','.join(ports) if ports else '-'
        
        # Format row
        click.echo(f"{branch:<20} {container_id:<12} {status:<10} {editor:<10} {ports_str:<20}")
        
        # Show modules if any
        if modules:
            click.echo(f"{'':>20} modules: {modules}")
    
    click.echo(f"\nTotal: {len(containers)} containers")
