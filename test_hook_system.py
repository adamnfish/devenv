#!/usr/bin/env python3
"""
Simple manual test of the hook system functionality.
"""

import os
import sys

# Add the src directory to the Python path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'src'))

from devenv.utils.hooks import (
    find_hook_scripts,
    generate_hook_command,
    generate_post_create_command_with_hooks,
    generate_post_start_command_with_hooks
)

def test_hook_discovery():
    """Test hook script discovery."""
    print("=== Testing Hook Discovery ===")
    
    project_path = "test-sandbox/hook-test/test-project"
    home_dir = "/tmp/fake-home"  # Use fake home to avoid real user config
    
    print(f"Looking for hooks in: {project_path}")
    hooks = find_hook_scripts("post_create", project_path, home_dir)
    
    print(f"Found {len(hooks)} hooks:")
    for source, path in hooks:
        print(f"  {source}: {path}")
        print(f"    Executable: {os.access(path, os.X_OK)}")
    
    return len(hooks) > 0

def test_command_generation():
    """Test hook command generation."""
    print("\n=== Testing Hook Command Generation ===")
    
    project_path = "test-sandbox/hook-test/test-project"
    home_dir = "/tmp/fake-home"
    
    # Test post_create command generation
    config = {
        "name": "test-project",
        "image": "ubuntu:24.04",
        "post_create_command": "echo 'User post_create command executed'"
    }
    
    print("Generating post_create command...")
    command = generate_post_create_command_with_hooks(config, project_path, home_dir)
    
    print("Generated command:")
    print("=" * 80)
    print(command)
    print("=" * 80)
    
    # Verify key components are present
    checks = [
        ("mise setup", "curl -fsSL https://mise.run | sh" in command),
        ("mise install", "~/.local/bin/mise install" in command),
        ("hook execution", "post_create" in command and "Executing project post_create hook" in command),
        ("user command", "User post_create command executed" in command),
        ("mise context", "source ~/.bashrc" in command)
    ]
    
    print("\nCommand verification:")
    all_passed = True
    for check_name, passed in checks:
        status = "✓" if passed else "✗"
        print(f"  {status} {check_name}")
        if not passed:
            all_passed = False
    
    return all_passed

def test_post_start_generation():
    """Test post_start command generation."""
    print("\n=== Testing Post Start Command Generation ===")
    
    # Create a post_start hook
    post_start_hook = "test-sandbox/hook-test/test-project/.devenv/hooks/post_start"
    with open(post_start_hook, 'w') as f:
        f.write("""#!/bin/bash
echo "=== POST_START HOOK EXECUTED ==="
echo "Starting development server..."
echo "=== POST_START HOOK COMPLETE ==="
""")
    os.chmod(post_start_hook, 0o755)
    
    project_path = "test-sandbox/hook-test/test-project"
    home_dir = "/tmp/fake-home"
    
    config = {
        "name": "test-project",
        "image": "ubuntu:24.04",
        "post_start_command": "echo 'User post_start command executed'"
    }
    
    print("Generating post_start command...")
    command = generate_post_start_command_with_hooks(config, project_path, home_dir)
    
    if command:
        print("Generated command:")
        print("=" * 80)
        print(command)
        print("=" * 80)
        
        checks = [
            ("hook execution", "post_start" in command and "Executing project post_start hook" in command),
            ("user command", "User post_start command executed" in command),
            ("mise context", "source ~/.bashrc" in command)
        ]
        
        print("\nCommand verification:")
        all_passed = True
        for check_name, passed in checks:
            status = "✓" if passed else "✗"
            print(f"  {status} {check_name}")
            if not passed:
                all_passed = False
        
        return all_passed
    else:
        print("No post_start command generated")
        return False

def main():
    """Run all tests."""
    print("Testing devenv hook system functionality")
    print("=" * 50)
    
    try:
        # Run tests
        discovery_ok = test_hook_discovery()
        command_ok = test_command_generation()
        start_ok = test_post_start_generation()
        
        # Summary
        print(f"\n=== Test Summary ===")
        print(f"Hook discovery: {'✓' if discovery_ok else '✗'}")
        print(f"Command generation: {'✓' if command_ok else '✗'}")
        print(f"Post start generation: {'✓' if start_ok else '✗'}")
        
        if discovery_ok and command_ok and start_ok:
            print("\n🎉 All tests passed! Hook system is working correctly.")
            return 0
        else:
            print("\n❌ Some tests failed. Check the output above.")
            return 1
            
    except Exception as e:
        print(f"\n💥 Test execution failed: {e}")
        import traceback
        traceback.print_exc()
        return 1

if __name__ == "__main__":
    sys.exit(main())