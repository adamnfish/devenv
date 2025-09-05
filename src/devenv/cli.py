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
from .utils.modules import validate_modules, list_modules


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
    
    # Parse and validate modules list
    modules_list = [m.strip() for m in modules.split(',')] if modules else []
    if modules_list:
        try:
            validate_modules(modules_list)
        except ValueError as e:
            click.echo(f"Error: {e}", err=True)
            exit(1)
    
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
@click.argument('branch')
@click.option('--editor', type=click.Choice(['vscode', 'jetbrains']), 
              help='Override editor type for this session')
@handle_docker_errors  
def switch(branch, editor):
    """Switch to an existing dev container for the specified branch"""
    
    # Get repository name
    repo = os.path.basename(os.path.abspath('.'))
    
    # Find existing container
    container = find_container_by_branch(branch, repo)
    
    if not container:
        click.echo(f"No container found for branch '{branch}' in repository '{repo}'")
        click.echo("Available containers:")
        
        # Show available containers for this repo
        all_containers = find_devenv_containers()
        repo_containers = [c for c in all_containers 
                          if c.attrs.get('Config', {}).get('Labels', {}).get('com.devenv.repo') == repo]
        
        if repo_containers:
            for c in repo_containers:
                labels = c.attrs.get('Config', {}).get('Labels', {})
                branch_name = labels.get('com.devenv.branch', 'unknown')
                editor_type = labels.get('com.devenv.editor', 'unknown')
                click.echo(f"  - {branch_name} ({editor_type})")
        else:
            click.echo("  None found for this repository")
        
        exit(1)
    
    # Get container info
    labels = container.attrs.get('Config', {}).get('Labels', {})
    container_editor = labels.get('com.devenv.editor', 'vscode')
    container_name = container.name
    
    # Use specified editor or default to container's editor
    target_editor = editor or container_editor
    
    # Check if container is running
    if container.status != 'running':
        click.echo(f"Starting container: {container_name}")
        try:
            container.start()
            click.echo("Container started successfully")
        except Exception as e:
            click.echo(f"Error starting container: {e}", err=True)
            exit(1)
    
    click.echo(f"Connecting to container: {container_name}")
    click.echo(f"Branch: {branch}")
    click.echo(f"Editor: {target_editor}")
    
    # Launch IDE based on editor choice
    if target_editor == 'vscode':
        try:
            # Get the workspace folder from container labels
            workspace = labels.get('devcontainer.local_folder', '.')
            click.echo("Launching VS Code...")
            result = subprocess.run(['code', workspace], capture_output=True)
            
            if result.returncode == 0:
                click.echo("✓ VS Code launched successfully")
            else:
                click.echo("Warning: VS Code may not have launched correctly")
                click.echo("You can also connect manually via VS Code's Remote-Containers extension")
                
        except FileNotFoundError:
            click.echo("VS Code not found in PATH")
            click.echo("Please install VS Code or connect manually via Remote-Containers extension")
        except Exception as e:
            click.echo(f"Error launching VS Code: {e}")
            click.echo("You can connect manually via VS Code's Remote-Containers extension")
    
    elif target_editor == 'jetbrains':
        click.echo("For JetBrains IDEs:")
        click.echo("1. Open your JetBrains IDE (IntelliJ, PyCharm, etc.)")
        click.echo(f"2. Use 'Services' panel to connect to container: {container_name}")
        click.echo("3. Or use the Dev Containers plugin if available")
        
        # Try to show container connection info
        click.echo(f"\nContainer details:")
        click.echo(f"  Name: {container_name}")
        click.echo(f"  ID: {container.short_id}")
    
    else:
        click.echo(f"Editor '{target_editor}' not supported")
        click.echo("Supported editors: vscode, jetbrains")
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


@cli.command()
@click.argument('branch')
@click.option('--volumes', is_flag=True, help='Also remove associated volumes')
@click.option('--force', '-f', is_flag=True, help='Force removal without confirmation')
@handle_docker_errors
def rm(branch, volumes, force):
    """Remove a dev container for the specified branch"""
    
    # Get repository name
    repo = os.path.basename(os.path.abspath('.'))
    
    # Find existing container
    container = find_container_by_branch(branch, repo)
    
    if not container:
        click.echo(f"No container found for branch '{branch}' in repository '{repo}'")
        exit(1)
    
    # Get container info
    labels = container.attrs.get('Config', {}).get('Labels', {})
    container_name = container.name
    container_id = container.short_id
    editor = labels.get('com.devenv.editor', 'unknown')
    modules = labels.get('com.devenv.modules', '')
    
    # Show container info
    click.echo(f"Container to remove:")
    click.echo(f"  Branch: {branch}")
    click.echo(f"  Name: {container_name}")
    click.echo(f"  ID: {container_id}")
    click.echo(f"  Editor: {editor}")
    if modules:
        click.echo(f"  Modules: {modules}")
    click.echo(f"  Status: {container.status}")
    
    # Confirmation unless forced
    if not force:
        if not click.confirm(f"Are you sure you want to remove this container?"):
            click.echo("Removal cancelled.")
            return
    
    try:
        # Stop container if running
        if container.status == 'running':
            click.echo("Stopping container...")
            container.stop(timeout=10)
            click.echo("Container stopped")
        
        # Remove container
        click.echo("Removing container...")
        container.remove(v=volumes)  # v=volumes removes anonymous volumes
        
        click.echo(f"✓ Container removed: {container_name}")
        
        if volumes:
            click.echo("✓ Associated volumes removed")
            
    except Exception as e:
        click.echo(f"Error removing container: {e}", err=True)
        exit(1)


@cli.command()
def modules():
    """List available built-in modules"""
    list_modules()
